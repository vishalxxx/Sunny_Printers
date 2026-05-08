package service;

import java.sql.Connection;

import model.JobItem;
import model.Printing;
import repository.JobItemRepository;
import repository.PrintingItemRepository;
import utils.DBConnection;

public class PrintingItemService {

    private final JobItemRepository jobItemRepo = new JobItemRepository();
    private final PrintingItemRepository printingRepo = new PrintingItemRepository();

	public JobItem addPrinting(int jobId, Printing p) {

        if (jobId <= 0) {
            throw new IllegalArgumentException("Job not created");
        }

        // ✅ VALIDATION (your rules)
        boolean hasAllMainFields =
                p.getQty() > 0 &&
                p.getUnits() != null && !p.getUnits().isBlank() &&
                p.getSets() != null && !p.getSets().isBlank() &&
                p.getAmount() > 0;

        boolean hasNotesAndAmount =
                p.getNotes() != null && !p.getNotes().isBlank() &&
                p.getAmount() > 0;

        boolean isValid = hasAllMainFields || hasNotesAndAmount;

        if (!isValid) {
            throw new IllegalArgumentException(
                "Enter Printing details OR Notes + Amount"
            );
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            // ✅ Step 1: Save JobItem
            JobItem item = new JobItem();
            item.setJobId(jobId);
            item.setType("PRINTING");
            item.setDescription(buildPrintingDescription(p));  // short summary
            item.setAmount(p.getAmount());
            item.setSortOrder(1);

            JobItem savedItem = jobItemRepo.save(con, item);

            // ✅ Step 2: Save Printing Details table
            printingRepo.save(con, savedItem.getId(), p);

            con.commit();
            return savedItem;

        } catch (Exception e) {
            throw new RuntimeException("Failed to add printing item", e);
        }
    }

    private String buildPrintingDescription(Printing p) {

        StringBuilder desc = new StringBuilder();

        if (p.getQty() > 0 && p.getUnits() != null) {
            desc.append(p.getQty()).append(" ").append(p.getUnits());
        }

        if (p.getSets() != null && !p.getSets().isBlank()) {
            desc.append(" - ").append(p.getSets()).append(" sets");
        }

        if (p.getColor() != null && !p.getColor().isBlank()) {
            desc.append(" - ").append(p.getColor());
        }

        if (p.getSide() != null && !p.getSide().isBlank()) {
            desc.append(" - ").append(p.getSide());
        }

        if (p.isWithCtp()) {
            desc.append(" - with CTP");
        }

        // ✅ if only notes, show notes as description
        if ((desc.toString().isBlank()) && p.getNotes() != null && !p.getNotes().isBlank()) {
            desc.append(p.getNotes());
        }

        return desc.toString().trim();
    }
}
