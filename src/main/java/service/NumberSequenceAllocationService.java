package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Optional;

import api.supabase.SupabaseGate;
import api.supabase.sequences.NumberSequencesSupabaseApi;
import model.MasterDocumentSeries;
import model.NumberSequence;
import model.SystemSettings;
import repository.NumberSequenceRepository;
import repository.SystemSettingsRepository;
import utils.ClientIdentifiers;
import utils.CompanyProfile;
import utils.DocumentNumbering;
import utils.NumberSequenceCatalog;
import service.sync.UniversalTemporaryNumberEngine;

/**
 * Allocates document numbers from Supabase {@code number_sequences} when reachable;
 * otherwise issues local TEMP-* numbers without advancing the remote counter.
 */
public class NumberSequenceAllocationService {

	public record AllocatedNumber(String value, boolean temporary) {
	}

	private final NumberSequenceRepository numberSeqRepo = new NumberSequenceRepository();
	private final SystemSettingsRepository settingsRepo = new SystemSettingsRepository();

	/** True when Supabase is configured and the row can be read. */
	public boolean canReachSupabaseFor(String sequenceKey) {
		if (sequenceKey == null || sequenceKey.isBlank()) {
			return false;
		}
		return SupabaseGate.restClientIfConfigured().map(http -> {
			try {
				return new NumberSequencesSupabaseApi(http).fetchByKey(sequenceKey.trim()).isPresent();
			} catch (Exception e) {
				return false;
			}
		}).orElse(false);
	}

	public AllocatedNumber allocate(Connection con, MasterDocumentSeries series, LocalDate refDate) throws Exception {
		String key = NumberSequenceCatalog.moduleNameFor(series);
		if (key == null) {
			throw new IllegalArgumentException("series");
		}
		return allocate(con, key, refDate);
	}

	public AllocatedNumber allocate(Connection con, String sequenceKey, LocalDate refDate) throws Exception {
		LocalDate d = refDate != null ? refDate : LocalDate.now();
		String key = sequenceKey.trim();
		Optional<NumberSequence> remoteRow = fetchRemoteRow(key);

		if (remoteRow.isPresent()) {
			NumberSequence row = remoteRow.get();
			alignFinancialYear(row, d);
			long next = row.getCurrentNumber() + 1;
			row.setCurrentNumber(next);
			persistRemoteIncrement(row);
			mirrorLocal(con, row);
			mirrorLegacySettings(con, key, next, row.getDigitWidth(), row.getFinancialYear());
			String cp = DocumentNumbering.companyPrefixFromTradeName(CompanyProfile.getName());
			int pad = Math.max(1, row.getDigitWidth());
			String fy = row.getFinancialYear() != null && !row.getFinancialYear().isBlank()
					? row.getFinancialYear()
					: DocumentNumbering.financialYearLabel(d);
			String formatted = DocumentNumbering.formatMasterLine(cp, row.getPrefix(), fy, next, pad);
			return new AllocatedNumber(formatted, false);
		}

		return allocateTemporary(con, key, d);
	}

	public AllocatedNumber allocateClientCode(Connection con) throws Exception {
		return allocate(con, "client", LocalDate.now());
	}

	/**
	 * Allocates from Supabase {@code number_sequences} only (no TEMP-* fallback).
	 * Used when promoting offline clients after connectivity returns.
	 */
	public Optional<AllocatedNumber> tryAllocatePermanent(Connection con, String sequenceKey, LocalDate refDate)
			throws Exception {
		LocalDate d = refDate != null ? refDate : LocalDate.now();
		String key = sequenceKey.trim();
		Optional<NumberSequence> remoteRow = fetchRemoteRow(key);
		if (remoteRow.isEmpty()) {
			return Optional.empty();
		}
		NumberSequence row = remoteRow.get();
		alignFinancialYear(row, d);
		long next = row.getCurrentNumber() + 1;
		row.setCurrentNumber(next);
		persistRemoteIncrement(row);
		mirrorLocal(con, row);
		mirrorLegacySettings(con, key, next, row.getDigitWidth(), row.getFinancialYear());
		String cp = DocumentNumbering.companyPrefixFromTradeName(CompanyProfile.getName());
		int pad = Math.max(1, row.getDigitWidth());
		String fy = row.getFinancialYear() != null && !row.getFinancialYear().isBlank()
				? row.getFinancialYear()
				: DocumentNumbering.financialYearLabel(d);
		String formatted = DocumentNumbering.formatMasterLine(cp, row.getPrefix(), fy, next, pad);
		return Optional.of(new AllocatedNumber(formatted, false));
	}

	public Optional<AllocatedNumber> tryAllocatePermanentClientCode(Connection con) throws Exception {
		return tryAllocatePermanent(con, "client", LocalDate.now());
	}

	public String allocateJobCode(Connection con) throws Exception {
		return allocate(con, "job", LocalDate.now()).value();
	}

	public static final String PAYMENT_RECEIPT_DETAIL_KEY = "receipt_no";

	public String allocatePaymentReceiptNo(Connection con, LocalDate paymentDate) throws Exception {
		AllocatedNumber a = allocate(con, "payment_receipt", paymentDate);
		return a.value();
	}

	/** Display or PDF: reuse stored receipt; legacy rows use id-based format without allocating. */
	public String resolvePaymentReceiptNo(Connection con, String paymentUuid, LocalDate paymentDate, boolean allocateIfMissing)
			throws Exception {
		String stored = readStoredReceiptNo(con, paymentUuid);
		if (stored != null && !stored.isBlank()) {
			return stored.trim();
		}
		if (!allocateIfMissing) {
			// fallback to a string representation if possible, though UUIDs don't have a clear sequence format
			return "PAY-" + (paymentUuid.length() > 8 ? paymentUuid.substring(0, 8) : paymentUuid);
		}
		LocalDate d = paymentDate != null ? paymentDate : LocalDate.now();
		String receiptNo = allocatePaymentReceiptNo(con, d);
		persistReceiptNo(con, paymentUuid, receiptNo);
		return receiptNo;
	}

	public void persistReceiptNo(Connection con, String paymentUuid, String receiptNo) throws Exception {
		if (receiptNo == null || receiptNo.isBlank()) {
			return;
		}
		try (PreparedStatement ps = con.prepareStatement("""
				INSERT INTO payment_details (uuid, payment_uuid, field_key, field_value, sync_status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'PENDING', datetime('now'), datetime('now'))
				ON CONFLICT(payment_uuid, field_key) DO UPDATE SET
				    field_value = excluded.field_value,
				    updated_at = datetime('now'),
				    sync_status = 'PENDING'
				""")) {
			ps.setString(1, ClientIdentifiers.newUuidV7String());
			ps.setString(2, paymentUuid);
			ps.setString(3, PAYMENT_RECEIPT_DETAIL_KEY);
			ps.setString(4, receiptNo.trim());
			ps.executeUpdate();
		}
	}

	private static String readStoredReceiptNo(Connection con, String paymentUuid) throws Exception {
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT field_value FROM payment_details WHERE payment_uuid = ? AND field_key = ?")) {
			ps.setString(1, paymentUuid);
			ps.setString(2, PAYMENT_RECEIPT_DETAIL_KEY);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getString(1);
				}
			}
		}
		return null;
	}

	private Optional<NumberSequence> fetchRemoteRow(String sequenceKey) {
		return SupabaseGate.restClientIfConfigured().flatMap(http -> {
			try {
				return new NumberSequencesSupabaseApi(http).fetchByKey(sequenceKey);
			} catch (Exception e) {
				System.err.println("[number_sequences] remote read failed for " + sequenceKey + ": " + e.getMessage());
				return Optional.empty();
			}
		});
	}

	private void persistRemoteIncrement(NumberSequence row) {
		SupabaseGate.restClientIfConfigured().ifPresent(http -> {
			try {
				new NumberSequencesSupabaseApi(http).upsertAll(java.util.List.of(row));
			} catch (Exception e) {
				System.err.println("[number_sequences] remote write failed: " + e.getMessage());
			}
		});
	}

	private AllocatedNumber allocateTemporary(Connection con, String sequenceKey, LocalDate refDate) throws Exception {
		return UniversalTemporaryNumberEngine.getInstance().allocateTemporary(con, sequenceKey);
	}

	private void mirrorLocal(Connection con, NumberSequence row) throws Exception {
		numberSeqRepo.upsert(con, row);
	}

	private void mirrorLegacySettings(Connection con, String sequenceKey, long next, int pad, String fy)
			throws Exception {
		SystemSettings s = settingsRepo.load(con);
		s.alignFinancialYearTo(LocalDate.now());
		NumberSequenceCatalog.seriesForModule(sequenceKey).ifPresent(series -> s.setLastSeq(series, (int) next));
		if ("temp_invoice".equals(sequenceKey)) {
			s.setLastTempInvoiceNo((int) next);
		}
		if (pad > 0) {
			s.setInvoicePadding(pad);
			s.setJobPadding(pad);
		}
		if (fy != null && !fy.isBlank()) {
			s.setNumberingFy(fy.trim());
		}
		settingsRepo.save(con, s);
	}

	private static void alignFinancialYear(NumberSequence row, LocalDate refDate) {
		String fy = DocumentNumbering.financialYearLabel(refDate);
		if (row.getFinancialYear() == null || row.getFinancialYear().isBlank()) {
			row.setFinancialYear(fy);
		}
	}
}
