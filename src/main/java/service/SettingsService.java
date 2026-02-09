package service;

import java.sql.Connection;

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
	public synchronized String[] generateNextInvoiceNumbers(Connection con, int count) throws Exception {

	    if (count <= 0) {
	        return new String[0];
	    }

	    // 1️⃣ Load settings ONLY once
	    SystemSettings s = repo.load();

	    String[] numbers = new String[count];

	    int current = s.getLastInvoiceNo();

	    // 2️⃣ Generate numbers IN MEMORY (no DB writes here)
	    for (int i = 0; i < count; i++) {

	        if (s.isAuto()) {
	            current = current + 1;
	        } else {
	            if (current < s.getInvoiceStartNo()) {
	                current = s.getInvoiceStartNo();
	            } else {
	                current = current + 1;
	            }
	        }

	        numbers[i] = String.format(
	                "%s%0" + s.getInvoicePadding() + "d",
	                s.getInvoicePrefix(),
	                current
	        );
	    }

	    // 3️⃣ Save ONLY ONCE → prevents SQLITE_BUSY
	    s.setLastInvoiceNo(current);
	    repo.save(s);

	    return numbers;
	}

	
}
