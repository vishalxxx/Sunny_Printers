package service;

import java.sql.Connection;
import java.util.List;

import model.Binding;
import model.CtpPlate;
import model.JobItem;
import model.Lamination;
import model.Paper;
import model.Printing;
import repository.BindingItemRepository;
import repository.CtpItemRepository;
import repository.JobItemRepository;
import repository.LaminationItemRepository;
import repository.PaperItemRepository;
import repository.PrintingItemRepository;
import utils.DBConnection;

public class JobItemService {

    /* =====================================================
       REPOSITORIES
       ===================================================== */
    private final JobItemRepository jobItemRepo = new JobItemRepository();
    private final PrintingItemRepository printingRepo = new PrintingItemRepository();
    private final PaperItemRepository paperRepo = new PaperItemRepository();
    private final BindingItemRepository bindingRepo = new BindingItemRepository();
    private final LaminationItemRepository laminationRepo = new LaminationItemRepository();
    private final CtpItemRepository ctpRepo = new CtpItemRepository();

    /* =====================================================
       CONNECTION (NULL for read-only usage)
       ===================================================== */
    private final Connection con;

    /* =====================================================
       CONSTRUCTORS
       ===================================================== */

    // ✅ READ MODE (used by tabs for loading)
    public JobItemService() {
        this.con = null;
    }

    // ✅ WRITE MODE (used by EditJobController during Save)
    public JobItemService(Connection con) {
        this.con = con;
    }

    /* =====================================================
       READ OPERATIONS
       ===================================================== */

    public List<JobItem> getJobItems(String jobUuid) {
        try {
            return jobItemRepo.findByJobUuid(jobUuid);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load job items", e);
        }
    }

    /** Loads typed line-item cards (Printing, Paper, …) for edit/add-job UIs. */
    public java.util.List<Object> loadJobItemCards(String jobUuid) {
        if (jobUuid == null || jobUuid.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<Object> cards = new java.util.ArrayList<>();
        for (JobItem ji : getJobItems(jobUuid)) {
            String type = ji.getType() != null ? ji.getType().trim().toUpperCase() : "";
            switch (type) {
                case "PRINTING" -> {
                    Printing p = printingRepo.findByJobItemUuid(ji.getUuid());
                    if (p != null) cards.add(p);
                }
                case "PAPER" -> {
                    Paper p = paperRepo.findByJobItemUuid(ji.getUuid());
                    if (p != null) cards.add(p);
                }
                case "BINDING" -> {
                    Binding b = bindingRepo.findByJobItemUuid(ji.getUuid());
                    if (b != null) cards.add(b);
                }
                case "LAMINATION" -> {
                    Lamination l = laminationRepo.findByJobItemUuid(ji.getUuid());
                    if (l != null) cards.add(l);
                }
                case "CTP" -> {
                    CtpPlate c = ctpRepo.findByJobItemUuid(ji.getUuid());
                    if (c != null) cards.add(c);
                }
                default -> cards.add(ji);
            }
        }
        return cards;
    }

    /* =====================================================
       WRITE OPERATION (NO COMMIT HERE)
       ===================================================== */

    public JobItem addJobItem(String jobUuid, Object cardData) {
        if (con == null) {
            try (Connection autoCon = DBConnection.getConnection()) {
                autoCon.setAutoCommit(false);
                try {
                    JobItem result = addJobItem(autoCon, jobUuid, cardData);
                    autoCon.commit();
                    return result;
                } catch (Exception e) {
                    autoCon.rollback();
                    throw e;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to add job item: " + e.getMessage(), e);
            }
        } else {
            return addJobItem(this.con, jobUuid, cardData);
        }
    }
    
    public JobItem addJobItem(Connection con, String jobUuid, Object cardData) {
    	 if (con == null)
             throw new IllegalStateException("Connection not provided (transaction required)");

         if (jobUuid == null || jobUuid.isBlank())
             throw new IllegalArgumentException("Invalid job uuid");

         if (cardData == null)
             throw new IllegalArgumentException("Card data is null");

         JobItem savedJobItem;

         // ========================= PRINTING =========================
         if (cardData instanceof Printing p) {
             validatePrinting(p);
             JobItem ji = new JobItem();
             ji.setJobUuid(jobUuid);
             ji.setType("PRINTING");
             ji.setDescription(buildPrintingDescription(p));
             ji.setAmount(p.getAmount());
             ji.setSortOrder(1);

             savedJobItem = jobItemRepo.save(con, ji);
             p.setJobItemUuid(savedJobItem.getUuid());
             printingRepo.save(con, savedJobItem.getUuid(), p);
         }

         // ========================= PAPER =========================
         else if (cardData instanceof Paper p) {
             validatePaper(p);
             JobItem ji = new JobItem();
             ji.setJobUuid(jobUuid);
             ji.setType("PAPER");
             ji.setDescription(buildPaperDescription(p));
             ji.setAmount(p.getAmount());
             ji.setSortOrder(2);

             savedJobItem = jobItemRepo.save(con, ji);
             p.setJobItemUuid(savedJobItem.getUuid());
             paperRepo.save(con, savedJobItem.getUuid(), p);
         }

         // ========================= BINDING =========================
         else if (cardData instanceof Binding b) {
             validateBinding(b);
             JobItem ji = new JobItem();
             ji.setJobUuid(jobUuid);
             ji.setType("BINDING");
             ji.setDescription(buildBindingDescription(b));
             ji.setAmount(b.getAmount());
             ji.setSortOrder(3);

             savedJobItem = jobItemRepo.save(con, ji);
             b.setJobItemUuid(savedJobItem.getUuid());
             bindingRepo.save(con, savedJobItem.getUuid(), b);
         }

         // ========================= LAMINATION =========================
         else if (cardData instanceof Lamination l) {
             validateLamination(l);
             JobItem ji = new JobItem();
             ji.setJobUuid(jobUuid);
             ji.setType("LAMINATION");
             ji.setDescription(buildLaminationDescription(l));
             ji.setAmount(l.getAmount());
             ji.setSortOrder(4);

             savedJobItem = jobItemRepo.save(con, ji);
             l.setJobItemUuid(savedJobItem.getUuid());
             laminationRepo.save(con, savedJobItem.getUuid(), l);
         }

         // ========================= CTP =========================
         else if (cardData instanceof CtpPlate ctp) {
             validateCtp(ctp);
             JobItem ji = new JobItem();
             ji.setJobUuid(jobUuid);
             ji.setType("CTP");
             ji.setDescription(buildCtpDescription(ctp));
             ji.setAmount(ctp.getAmount());
             ji.setSortOrder(5);

             savedJobItem = jobItemRepo.save(con, ji);
             ctp.setJobItemUuid(savedJobItem.getUuid());
             ctpRepo.save(con, savedJobItem.getUuid(), ctp);
         }

         else {
             throw new IllegalArgumentException(
                 "Unsupported card type: " + cardData.getClass().getName()
             );
         }

         return savedJobItem;
    }

    
    

    /* =====================================================
       VALIDATIONS
       ===================================================== */

    private void validatePrinting(Printing p) {
        if (p.getAmount() <= 0)
            throw new IllegalArgumentException("Printing amount required");
    }

    private void validatePaper(Paper p) {
        if (p.getAmount() <= 0)
            throw new IllegalArgumentException("Paper amount required");
    }

    private void validateBinding(Binding b) {
        if (b.getAmount() <= 0)
            throw new IllegalArgumentException("Binding amount required");
    }

    private void validateLamination(Lamination l) {
        if (l.getAmount() <= 0)
            throw new IllegalArgumentException("Lamination amount required");
    }

    private void validateCtp(CtpPlate c) {
        if (c.getQty() <= 0)
            throw new IllegalArgumentException("CTP qty required");
        if (c.getAmount() <= 0)
            throw new IllegalArgumentException("CTP amount required");
    }

    /* =====================================================
       DESCRIPTION BUILDERS
       ===================================================== */

    public String buildPrintingDescription(Printing p) {
        StringBuilder sb = new StringBuilder("Printing ");
        if (p.getQty() > 0) sb.append(p.getQty()).append(" ");
        if (p.getUnits() != null && !p.getUnits().equalsIgnoreCase("Select Unit")) sb.append(p.getUnits()).append(" ");
        if (p.getSets() != null && !p.getSets().isBlank()) sb.append("[").append(p.getSets()).append(" Sets] ");
        if (p.getColor() != null && !p.getColor().equalsIgnoreCase("Select Color")) sb.append(p.getColor()).append(" ");
        if (p.isWithCtp()) sb.append("with CTP ");
        if (p.isIncludeNotesInInvoice() && p.getNotes() != null && !p.getNotes().isBlank())
            sb.append("- ").append(p.getNotes());
        return sb.toString().trim();
    }

    public String buildPaperDescription(Paper p) {
        StringBuilder sb = new StringBuilder("Paper ");
        if (p.getQty() > 0) sb.append(p.getQty()).append(" ");
        if (p.getUnits() != null && !p.getUnits().equalsIgnoreCase("Select Unit")) sb.append(p.getUnits()).append(" ");
        if (p.getSize() != null && !p.getSize().equalsIgnoreCase("Select Size")) sb.append(p.getSize()).append(" ");
        if (p.getGsm() != null && !p.getGsm().equalsIgnoreCase("Select GSM")) sb.append(p.getGsm()).append(" GSM ");
        if (p.getType() != null && !p.getType().equalsIgnoreCase("Select Type")) sb.append(p.getType()).append(" ");
        if (p.getSource() != null) sb.append("(").append(p.getSource()).append(") ");
        if (p.isIncludeNotesInInvoice() && p.getNotes() != null && !p.getNotes().isBlank())
            sb.append("- ").append(p.getNotes());
        return sb.toString().trim();
    }

    public String buildBindingDescription(Binding b) {
        StringBuilder sb = new StringBuilder("Binding ");
        if (b.getQty() > 0) sb.append(b.getQty()).append(" ");
        if (b.getProcess() != null && !b.getProcess().equalsIgnoreCase("Select Binding")) sb.append(b.getProcess()).append(" ");
        if (b.getRate() > 0) sb.append("@").append(b.getRate()).append(" ");
        if (b.isIncludeNotesInInvoice() && b.getNotes() != null && !b.getNotes().isBlank())
            sb.append("- ").append(b.getNotes());
        return sb.toString().trim();
    }

    public String buildLaminationDescription(Lamination l) {
        StringBuilder sb = new StringBuilder("Lamination ");
        if (l.getQty() > 0) sb.append(l.getQty()).append(" ");
        if (l.getUnit() != null && !l.getUnit().equalsIgnoreCase("Select Unit")) sb.append(l.getUnit()).append(" ");
        if (l.getType() != null && !l.getType().equalsIgnoreCase("Select Type")) sb.append(l.getType()).append(" ");
        if (l.getSide() != null) sb.append(l.getSide()).append(" ");
        if (l.getSize() != null && !l.getSize().equalsIgnoreCase("Select Size")) sb.append(l.getSize()).append(" ");
        if (l.isIncludeNotesInInvoice() && l.getNotes() != null && !l.getNotes().isBlank())
            sb.append("- ").append(l.getNotes());
        return sb.toString().trim();
    }

    public String buildCtpDescription(CtpPlate ctp) {
        StringBuilder sb = new StringBuilder("CTP Plate ");
        sb.append(ctp.getQty()).append(" pcs ");
        if (ctp.getPlateSize() != null && !ctp.getPlateSize().equalsIgnoreCase("Select Size")) sb.append(ctp.getPlateSize()).append(" ");
        if (ctp.getGauge() != null && !ctp.getGauge().equalsIgnoreCase("Select Gauge")) sb.append(ctp.getGauge()).append(" ");
        if (ctp.getBacking() != null && !ctp.getBacking().equalsIgnoreCase("Select Backing")) sb.append(ctp.getBacking()).append(" ");
        if (ctp.getColor() != null && !ctp.getColor().equalsIgnoreCase("Select Color")) sb.append(ctp.getColor()).append(" ");
        if (ctp.isIncludeNotesInInvoice() && ctp.getNotes() != null && !ctp.getNotes().isBlank())
            sb.append("- ").append(ctp.getNotes());
        return sb.toString().trim();
    }
}
