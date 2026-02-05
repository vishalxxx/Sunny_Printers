package service;

import model.SystemSettings;
import repository.SystemSettingsRepository;

public class SettingsService {

	private final SystemSettingsRepository repo = new SystemSettingsRepository();

	/**
	 * Generates the NEXT sequential invoice number and persists it immediately to
	 * DB.
	 */
	public synchronized String generateNextInvoiceNumber() throws Exception {

		SystemSettings s = repo.load();

		int nextNumber;

		if (s.isAuto()) {
			// AUTO → always increment
			nextNumber = s.getLastInvoiceNo() + 1;
		} else {
			// MANUAL → start from configured start_no if first time
			if (s.getLastInvoiceNo() < s.getInvoiceStartNo()) {
				nextNumber = s.getInvoiceStartNo();
			} else {
				nextNumber = s.getLastInvoiceNo() + 1;
			}
		}

		// Persist new last number
		s.setLastInvoiceNo(nextNumber);
		repo.save(s);

		// Format with prefix + padding
		return String.format("%s%0" + s.getInvoicePadding() + "d", s.getInvoicePrefix(), nextNumber);
	}
}
