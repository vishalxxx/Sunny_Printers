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

    public List<JobItem> getJobItems(int jobId) {
        return jobItemRepo.findByJobId(jobId);
    }

    /* =====================================================
       WRITE OPERATION (NO COMMIT HERE)
       ===================================================== */

    public JobItem addJobItem(int jobId, Object cardData) {

        if (con == null)
            throw new IllegalStateException("Connection not provided (transaction required)");

        if (jobId <= 0)
            throw new IllegalArgumentException("Invalid job id");

        if (cardData == null)
            throw new IllegalArgumentException("Card data is null");

        JobItem savedJobItem;

        // ========================= PRINTING =========================
        if (cardData instanceof Printing p) {

            validatePrinting(p);

            JobItem ji = new JobItem();
            ji.setJobId(jobId);
            ji.setType("PRINTING");
            ji.setDescription(buildPrintingDescription(p));
            ji.setAmount(p.getAmount());
            ji.setSortOrder(1);

            savedJobItem = jobItemRepo.save(con, ji);
            printingRepo.save(con, savedJobItem.getId(), p);
        }

        // ========================= PAPER =========================
        else if (cardData instanceof Paper p) {

            validatePaper(p);

            JobItem ji = new JobItem();
            ji.setJobId(jobId);
            ji.setType("PAPER");
            ji.setDescription(buildPaperDescription(p));
            ji.setAmount(p.getAmount());
            ji.setSortOrder(2);

            savedJobItem = jobItemRepo.save(con, ji);
            paperRepo.save(con, savedJobItem.getId(), p);
        }

        // ========================= BINDING =========================
        else if (cardData instanceof Binding b) {

            validateBinding(b);

            JobItem ji = new JobItem();
            ji.setJobId(jobId);
            ji.setType("BINDING");
            ji.setDescription(buildBindingDescription(b));
            ji.setAmount(b.getAmount());
            ji.setSortOrder(3);

            savedJobItem = jobItemRepo.save(con, ji);
            bindingRepo.save(con, savedJobItem.getId(), b);
        }

        // ========================= LAMINATION =========================
        else if (cardData instanceof Lamination l) {

            validateLamination(l);

            JobItem ji = new JobItem();
            ji.setJobId(jobId);
            ji.setType("LAMINATION");
            ji.setDescription(buildLaminationDescription(l));
            ji.setAmount(l.getAmount());
            ji.setSortOrder(4);

            savedJobItem = jobItemRepo.save(con, ji);
            laminationRepo.save(con, savedJobItem.getId(), l);
        }

        // ========================= CTP =========================
        else if (cardData instanceof CtpPlate ctp) {

            validateCtp(ctp);

            JobItem ji = new JobItem();
            ji.setJobId(jobId);
            ji.setType("CTP");
            ji.setDescription(buildCtpDescription(ctp));
            ji.setAmount(ctp.getAmount());
            ji.setSortOrder(5);

            savedJobItem = jobItemRepo.save(con, ji);
            ctpRepo.save(con, ctp);
        }

        else {
            throw new IllegalArgumentException(
                "Unsupported card type: " + cardData.getClass().getName()
            );
        }

        return savedJobItem;
    }
    
    public JobItem addJobItem(Connection con, int jobId, Object cardData) {
    	 if (con == null)
             throw new IllegalStateException("Connection not provided (transaction required)");

         if (jobId <= 0)
             throw new IllegalArgumentException("Invalid job id");

         if (cardData == null)
             throw new IllegalArgumentException("Card data is null");

         JobItem savedJobItem;

         // ========================= PRINTING =========================
         if (cardData instanceof Printing p) {

             validatePrinting(p);

             JobItem ji = new JobItem();
             ji.setJobId(jobId);
             ji.setType("PRINTING");
             ji.setDescription(buildPrintingDescription(p));
             ji.setAmount(p.getAmount());
             ji.setSortOrder(1);

             savedJobItem = jobItemRepo.save(con, ji);
             printingRepo.save(con, savedJobItem.getId(), p);
         }

         // ========================= PAPER =========================
         else if (cardData instanceof Paper p) {

             validatePaper(p);

             JobItem ji = new JobItem();
             ji.setJobId(jobId);
             ji.setType("PAPER");
             ji.setDescription(buildPaperDescription(p));
             ji.setAmount(p.getAmount());
             ji.setSortOrder(2);

             savedJobItem = jobItemRepo.save(con, ji);
             paperRepo.save(con, savedJobItem.getId(), p);
         }

         // ========================= BINDING =========================
         else if (cardData instanceof Binding b) {

             validateBinding(b);

             JobItem ji = new JobItem();
             ji.setJobId(jobId);
             ji.setType("BINDING");
             ji.setDescription(buildBindingDescription(b));
             ji.setAmount(b.getAmount());
             ji.setSortOrder(3);

             savedJobItem = jobItemRepo.save(con, ji);
             bindingRepo.save(con, savedJobItem.getId(), b);
         }

         // ========================= LAMINATION =========================
         else if (cardData instanceof Lamination l) {

             validateLamination(l);

             JobItem ji = new JobItem();
             ji.setJobId(jobId);
             ji.setType("LAMINATION");
             ji.setDescription(buildLaminationDescription(l));
             ji.setAmount(l.getAmount());
             ji.setSortOrder(4);

             savedJobItem = jobItemRepo.save(con, ji);
             laminationRepo.save(con, savedJobItem.getId(), l);
         }

         // ========================= CTP =========================
         else if (cardData instanceof CtpPlate ctp) {

             validateCtp(ctp);

             JobItem ji = new JobItem();
             ji.setJobId(jobId);
             ji.setType("CTP");
             ji.setDescription(buildCtpDescription(ctp));
             ji.setAmount(ctp.getAmount());
             ji.setSortOrder(5);

             savedJobItem = jobItemRepo.save(con, ji);
             ctpRepo.save(con, ctp);
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

    private String buildPrintingDescription(Printing p) {
        StringBuilder sb = new StringBuilder("Printing ");
        if (p.getQty() > 0) sb.append(p.getQty()).append(" ");
        if (p.getUnits() != null) sb.append(p.getUnits()).append(" ");
        if (p.getColor() != null) sb.append(p.getColor()).append(" ");
        if (p.isWithCtp()) sb.append("with CTP ");
        if (p.getNotes() != null && !p.getNotes().isBlank())
            sb.append("- ").append(p.getNotes());
        return sb.toString().trim();
    }

    private String buildPaperDescription(Paper p) {
        StringBuilder sb = new StringBuilder("Paper ");
        if (p.getQty() > 0) sb.append(p.getQty()).append(" ");
        if (p.getUnits() != null) sb.append(p.getUnits()).append(" ");
        if (p.getSize() != null) sb.append(p.getSize()).append(" ");
        if (p.getGsm() != null) sb.append(p.getGsm()).append(" GSM ");
        if (p.getType() != null) sb.append(p.getType()).append(" ");
        if (p.getSource() != null) sb.append("(").append(p.getSource()).append(") ");
        if (p.getNotes() != null && !p.getNotes().isBlank())
            sb.append("- ").append(p.getNotes());
        return sb.toString().trim();
    }

    private String buildBindingDescription(Binding b) {
        StringBuilder sb = new StringBuilder("Binding ");
        if (b.getQty() > 0) sb.append(b.getQty()).append(" ");
        if (b.getProcess() != null) sb.append(b.getProcess()).append(" ");
        if (b.getRate() > 0) sb.append("@").append(b.getRate()).append(" ");
        if (b.getNotes() != null && !b.getNotes().isBlank())
            sb.append("- ").append(b.getNotes());
        return sb.toString().trim();
    }

    private String buildLaminationDescription(Lamination l) {
        StringBuilder sb = new StringBuilder("Lamination ");
        if (l.getQty() > 0) sb.append(l.getQty()).append(" ");
        if (l.getUnit() != null) sb.append(l.getUnit()).append(" ");
        if (l.getType() != null) sb.append(l.getType()).append(" ");
        if (l.getSide() != null) sb.append(l.getSide()).append(" ");
        if (l.getSize() != null) sb.append(l.getSize()).append(" ");
        if (l.getNotes() != null && !l.getNotes().isBlank())
            sb.append("- ").append(l.getNotes());
        return sb.toString().trim();
    }

    private String buildCtpDescription(CtpPlate ctp) {
        StringBuilder sb = new StringBuilder("CTP Plate ");
        sb.append(ctp.getQty()).append(" pcs ");
        if (ctp.getPlateSize() != null) sb.append(ctp.getPlateSize()).append(" ");
        if (ctp.getGauge() != null) sb.append(ctp.getGauge()).append(" ");
        if (ctp.getBacking() != null) sb.append(ctp.getBacking()).append(" ");
        if (ctp.getColor() != null) sb.append(ctp.getColor()).append(" ");
        if (ctp.getNotes() != null && !ctp.getNotes().isBlank())
            sb.append("- ").append(ctp.getNotes());
        return sb.toString().trim();
    }
}
