package service;

import java.sql.Connection;
import java.time.LocalDate;

import model.MasterDocumentSeries;
import model.SystemSettings;
import model.NumberSequence;
import repository.SystemSettingsRepository;
import repository.NumberSequenceRepository;
import service.sync.UniversalNumberAllocator;
import utils.CompanyProfile;
import utils.DocumentNumbering;

public class SettingsService {

	private final SystemSettingsRepository repo = new SystemSettingsRepository();
	private final NumberSequenceAllocationService sequenceAllocator = new NumberSequenceAllocationService();
	private final UniversalNumberAllocator universalAllocator = UniversalNumberAllocator.getInstance();

	public String peekNextMasterNumberDisplay(Connection con, MasterDocumentSeries series, LocalDate refDate)
			throws Exception {
		if (series == null) {
			series = MasterDocumentSeries.GST_INVOICE;
		}
		if (series == MasterDocumentSeries.PROFORMA_INVOICE) {
			NumberSequenceRepository numberSeqRepo = new NumberSequenceRepository();
			NumberSequence local = numberSeqRepo.findByKey(con, "proforma_invoice");
			if (local != null) {
				long next = local.getOfflineCurrentNumber() + 1;
				int pad = Math.max(1, local.getDigitWidth() > 0 ? local.getDigitWidth() : 4);
				return DocumentNumbering.formatTemporary(local.getPrefix(), next, pad);
			} else {
				return "TEMP-PI-1";
			}
		}
		String key = utils.NumberSequenceCatalog.moduleNameFor(series);
		if (key != null && sequenceAllocator.canReachSupabaseFor(key)) {
			SystemSettings s = repo.load(con);
			LocalDate d = refDate != null ? refDate : LocalDate.now();
			s.alignFinancialYearTo(d);
			int last = s.getLastSeq(series);
			int next = last + 1;
			String cp = DocumentNumbering.companyPrefixFromTradeName(CompanyProfile.getName());
			int pad = Math.max(1, s.getInvoicePadding());
			return DocumentNumbering.formatMasterLine(cp, series.getTypeCode(), d, next, pad);
		}
		// OFFLINE mode: Show the next temporary invoice number
		if (key != null) {
			NumberSequenceRepository numberSeqRepo = new NumberSequenceRepository();
			NumberSequence local = numberSeqRepo.findByKey(con, key);
			if (local != null) {
				long next = local.getOfflineCurrentNumber() + 1;
				int pad = Math.max(1, local.getDigitWidth() > 0 ? local.getDigitWidth() : 4);
				return DocumentNumbering.formatTemporary(local.getPrefix(), next, pad);
			} else {
				String prefix = key.toUpperCase().replace("_", "");
				if (prefix.length() > 4) {
					prefix = prefix.substring(0, 4);
				}
				return "TEMP-" + prefix + "-1";
			}
		}
		SystemSettings s = repo.load(con);
		LocalDate d = refDate != null ? refDate : LocalDate.now();
		s.alignFinancialYearTo(d);
		int last = s.getLastSeq(series);
		int next = last + 1;
		String cp = DocumentNumbering.companyPrefixFromTradeName(CompanyProfile.getName());
		int pad = Math.max(1, s.getInvoicePadding());
		return DocumentNumbering.formatMasterLine(cp, series.getTypeCode(), d, next, pad);
	}

	public synchronized String allocateNextMasterNumber(Connection con, MasterDocumentSeries series, LocalDate refDate)
			throws Exception {
		if (series == null) {
			throw new IllegalArgumentException("series");
		}
		return universalAllocator.allocateInvoiceNumber(con, series, refDate).value();
	}

	public synchronized String generateNextInvoiceNumber(Connection con, LocalDate invoiceDate,
			MasterDocumentSeries series) throws Exception {
		MasterDocumentSeries s = series != null ? series : MasterDocumentSeries.GST_INVOICE;
		return allocateNextMasterNumber(con, s, invoiceDate != null ? invoiceDate : LocalDate.now());
	}

	public synchronized String generateNextInvoiceNumber(Connection con, LocalDate invoiceDate) throws Exception {
		return generateNextInvoiceNumber(con, invoiceDate, MasterDocumentSeries.GST_INVOICE);
	}

	public synchronized String[] generateNextInvoiceNumbers(Connection con, int count, LocalDate refDate)
			throws Exception {
		if (count <= 0) {
			return new String[0];
		}
		String[] numbers = new String[count];
		for (int i = 0; i < count; i++) {
			numbers[i] = allocateNextMasterNumber(con, MasterDocumentSeries.GST_INVOICE, refDate);
		}
		return numbers;
	}

	public synchronized String generateNextTempInvoiceNumber(Connection con) throws Exception {
		return universalAllocator.allocateTempInvoiceNumber(con).value();
	}

	public synchronized String[] generateNextTempInvoiceNumbers(Connection con, int count) throws Exception {
		if (count <= 0) {
			return new String[0];
		}
		String[] numbers = new String[count];
		for (int i = 0; i < count; i++) {
			numbers[i] = generateNextTempInvoiceNumber(con);
		}
		return numbers;
	}
}
