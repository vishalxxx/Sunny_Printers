package service;

import java.sql.Connection;

import model.SystemSettings;
import repository.SystemSettingsRepository;

public class SettingsService {

    private final SystemSettingsRepository repo = new SystemSettingsRepository();

    // =========================================================
    // SINGLE NUMBER (atomic)
    // =========================================================
    public synchronized String generateNextInvoiceNumber(Connection con) throws Exception {

        SystemSettings s = repo.load(con);

        int nextNumber;

        if (s.isAuto()) {
            nextNumber = s.getLastInvoiceNo() + 1;
        } else {
            if (s.getLastInvoiceNo() < s.getInvoiceStartNo()) {
                nextNumber = s.getInvoiceStartNo();
            } else {
                nextNumber = s.getLastInvoiceNo() + 1;
            }
        }

        // update in memory
        s.setLastInvoiceNo(nextNumber);

        // save using SAME transaction
        repo.save(con, s);

        return String.format(
                "%s%0" + s.getInvoicePadding() + "d",
                (s.getInvoicePrefix() != null ? s.getInvoicePrefix() : ""),
                nextNumber
        );
    }

    // =========================================================
    // BULK NUMBERS (atomic & SQLITE-safe)
    // =========================================================
    public synchronized String[] generateNextInvoiceNumbers(Connection con, int count) throws Exception {

        if (count <= 0) return new String[0];

        SystemSettings s = repo.load(con);

        String[] numbers = new String[count];
        int current = s.getLastInvoiceNo();

        for (int i = 0; i < count; i++) {

            if (s.isAuto()) {
                current++;
            } else {
                if (current < s.getInvoiceStartNo()) {
                    current = s.getInvoiceStartNo();
                } else {
                    current++;
                }
            }

            numbers[i] = String.format(
                    "%s%0" + s.getInvoicePadding() + "d",
                    (s.getInvoicePrefix() != null ? s.getInvoicePrefix() : ""),
                    current
            );
        }

        // 🔥 single DB write → prevents SQLITE_BUSY
        s.setLastInvoiceNo(current);
        repo.save(con, s);

        return numbers;
    }

    // =========================================================
    // TEMP NUMBERS
    // =========================================================
    public synchronized String generateNextTempInvoiceNumber(Connection con) throws Exception {
        SystemSettings s = repo.load(con);
        int next = s.getLastTempInvoiceNo() + 1;
        s.setLastTempInvoiceNo(next);
        repo.save(con, s);
        return String.format("TEMP-%03d", next);
    }

    public synchronized String[] generateNextTempInvoiceNumbers(Connection con, int count) throws Exception {
        if (count <= 0) return new String[0];
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
