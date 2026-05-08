package service;

import java.sql.Connection;
import java.time.LocalDate;

import model.MasterDocumentSeries;
import model.SystemSettings;
import repository.SystemSettingsRepository;
import utils.CompanyProfile;
import utils.DocumentNumbering;

public class SettingsService {

	private final SystemSettingsRepository repo = new SystemSettingsRepository();

	/**
	 * Next formatted number for {@code series} if allocated today for {@code refDate} (FY alignment
	 * only in memory — does not change the database).
	 */
	public String peekNextMasterNumberDisplay(Connection con, MasterDocumentSeries series, LocalDate refDate)
			throws Exception {
		if (series == null) {
			series = MasterDocumentSeries.GST_INVOICE;
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
		SystemSettings s = repo.load(con);
		LocalDate d = refDate != null ? refDate : LocalDate.now();
		s.alignFinancialYearTo(d);

		int last = s.getLastSeq(series);
		int next = last + 1;
		s.setLastSeq(series, next);

		if (series == MasterDocumentSeries.GST_INVOICE) {
			s.setLastInvoiceNo(next);
		} else if (series == MasterDocumentSeries.JOB) {
			s.setLastJobNo(next);
		}

		repo.save(con, s);

		String cp = DocumentNumbering.companyPrefixFromTradeName(CompanyProfile.getName());
		int pad = Math.max(1, s.getInvoicePadding());
		return DocumentNumbering.formatMasterLine(cp, series.getTypeCode(), d, next, pad);
	}

	/**
	 * Final invoice number using master format and invoice date for FY.
	 *
	 * @param series GST (INV…) or Proforma (PI…) sequence from invoice settings.
	 */
	public synchronized String generateNextInvoiceNumber(Connection con, LocalDate invoiceDate,
			MasterDocumentSeries series) throws Exception {
		MasterDocumentSeries s = series != null ? series : MasterDocumentSeries.GST_INVOICE;
		return allocateNextMasterNumber(con, s, invoiceDate != null ? invoiceDate : LocalDate.now());
	}

	/**
	 * Final invoice (GST) number — same as {@code generateNextInvoiceNumber(..., GST_INVOICE)}.
	 */
	public synchronized String generateNextInvoiceNumber(Connection con, LocalDate invoiceDate) throws Exception {
		return generateNextInvoiceNumber(con, invoiceDate, MasterDocumentSeries.GST_INVOICE);
	}

	public synchronized String[] generateNextInvoiceNumbers(Connection con, int count, LocalDate refDate)
			throws Exception {
		if (count <= 0) {
			return new String[0];
		}
		SystemSettings s = repo.load(con);
		LocalDate d = refDate != null ? refDate : LocalDate.now();
		s.alignFinancialYearTo(d);

		MasterDocumentSeries series = MasterDocumentSeries.GST_INVOICE;
		String cp = DocumentNumbering.companyPrefixFromTradeName(CompanyProfile.getName());
		int pad = Math.max(1, s.getInvoicePadding());

		String[] numbers = new String[count];
		int last = s.getLastSeq(series);
		for (int i = 0; i < count; i++) {
			last++;
			numbers[i] = DocumentNumbering.formatMasterLine(cp, series.getTypeCode(), d, last, pad);
		}
		s.setLastSeq(series, last);
		s.setLastInvoiceNo(last);
		repo.save(con, s);
		return numbers;
	}

	public synchronized String generateNextTempInvoiceNumber(Connection con) throws Exception {
		SystemSettings s = repo.load(con);
		int next = s.getLastTempInvoiceNo() + 1;
		s.setLastTempInvoiceNo(next);
		repo.save(con, s);
		return String.format("TEMP-%03d", next);
	}

	public synchronized String[] generateNextTempInvoiceNumbers(Connection con, int count) throws Exception {
		if (count <= 0) {
			return new String[0];
		}
		SystemSettings s = repo.load(con);
		String[] numbers = new String[count];
		int current = s.getLastTempInvoiceNo();
		for (int i = 0; i < count; i++) {
			current++;
			numbers[i] = String.format("TEMP-%03d", current);
		}
		s.setLastTempInvoiceNo(current);
		repo.save(con, s);
		return numbers;
	}
}
