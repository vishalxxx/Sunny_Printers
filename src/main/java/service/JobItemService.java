package service;

import java.sql.Connection;
import java.util.List;

import model.Binding;
import model.CtpPlate;
import model.JobItem;
import model.Lamination;
import model.Paper;
import model.Printing;
import model.Supplier;
import repository.BindingItemRepository;
import repository.CtpItemRepository;
import repository.JobItemRepository;
import repository.LaminationItemRepository;
import repository.PaperItemRepository;
import repository.PrintingItemRepository;
import utils.DBConnection;

public class JobItemService {

//	private final JobItemRepository repo = new JobItemRepository();
//	private final CtpItemRepository ctpRepo = new CtpItemRepository();
//	private final InvoiceGenerationService invoiceService = new InvoiceGenerationService();

	private final JobItemRepository jobItemRepo = new JobItemRepository();

	private final PrintingItemRepository printingRepo = new PrintingItemRepository();
	private final PaperItemRepository paperRepo = new PaperItemRepository();
	private final BindingItemRepository bindingRepo = new BindingItemRepository();
	private final LaminationItemRepository laminationRepo = new LaminationItemRepository();
	private final CtpItemRepository ctpRepo = new CtpItemRepository();
	
	 public List<JobItem> getJobItems(int jobId) {
	        return jobItemRepo.findByJobId(jobId);
	    }
	
	  // ✅ ONE METHOD FOR ALL CARD TYPES
    public JobItem addJobItem(int jobId, Object cardData) {

        if (jobId <= 0) throw new IllegalArgumentException("Job not created");
        if (cardData == null) throw new IllegalArgumentException("Card data is null");

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            JobItem savedJobItem;

            // =====================================================
            // ✅ PRINTING
            // =====================================================
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

            // =====================================================
            // ✅ PAPER
            // =====================================================
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

            // =====================================================
            // ✅ BINDING
            // =====================================================
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

            // =====================================================
            // ✅ LAMINATION
            // =====================================================
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

            // =====================================================
            // ✅ CTP PLATE
            // =====================================================
            else if (cardData instanceof CtpPlate ctp) {

                validateCtp(ctp);

                JobItem ji = new JobItem();
                ji.setJobId(jobId);
                ji.setType("CTP");
                ji.setDescription(buildCtpDescription(ctp));
                ji.setAmount(ctp.getAmount());
                ji.setSortOrder(5);

                savedJobItem = jobItemRepo.save(con, ji);

                // ✅ IMPORTANT: set job_item_id in CTP model
                ctp.setJobItemId(savedJobItem.getId());
                ctpRepo.save(con, ctp);
            }

            else {
                throw new IllegalArgumentException("Unsupported card type: " + cardData.getClass().getName());
            }

            con.commit();
            return savedJobItem;

        } catch (Exception e) {
            throw new RuntimeException("Failed to add JobItem", e);
        }
    }

    // =====================================================
    // ✅ VALIDATIONS
    // =====================================================

    private void validatePrinting(Printing p) {
        if (p.getAmount() <= 0) throw new IllegalArgumentException("Printing amount required");
        boolean hasNotes = p.getNotes() != null && !p.getNotes().isBlank();
        boolean hasFields = p.getQty() > 0 || (p.getUnits() != null && !p.getUnits().isBlank());
        if (!(hasNotes || hasFields)) throw new IllegalArgumentException("Enter printing details or notes");
    }

    private void validatePaper(Paper p) {
        if (p.getAmount() <= 0) throw new IllegalArgumentException("Paper amount required");
    }

    private void validateBinding(Binding b) {
        if (b.getAmount() <= 0) throw new IllegalArgumentException("Binding amount required");
    }

    private void validateLamination(Lamination l) {
        if (l.getAmount() <= 0) throw new IllegalArgumentException("Lamination amount required");
    }

    private void validateCtp(CtpPlate c) {
        if (c.getQty() <= 0) throw new IllegalArgumentException("CTP qty required");
        if (c.getAmount() <= 0) throw new IllegalArgumentException("CTP amount required");
        if (c.getColor() == null || c.getColor().isBlank()) throw new IllegalArgumentException("CTP color required");
        if (c.getSupplierId() == 0) throw new IllegalArgumentException("CTP supplier required");
    }

    // =====================================================
    // ✅ DESCRIPTION BUILDERS
    // =====================================================

    private String buildPrintingDescription(Printing p) {
        StringBuilder sb = new StringBuilder("Printing ");
        if (p.getQty() > 0) sb.append(p.getQty()).append(" ");
        if (p.getUnits() != null) sb.append(p.getUnits()).append(" ");
        if (p.getColor() != null) sb.append(p.getColor()).append(" ");
        if (p.isWithCtp()) sb.append("with CTP ");
        if (p.getNotes() != null && !p.getNotes().isBlank()) sb.append("- ").append(p.getNotes());
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
        if (p.getNotes() != null && !p.getNotes().isBlank()) sb.append("- ").append(p.getNotes());
        return sb.toString().trim();
    }

    private String buildBindingDescription(Binding b) {
        StringBuilder sb = new StringBuilder("Binding ");
        if (b.getQty() > 0) sb.append(b.getQty()).append(" ");
        if (b.getProcess() != null) sb.append(b.getProcess()).append(" ");
        if (b.getRate() > 0) sb.append("@").append(b.getRate()).append(" ");
        if (b.getNotes() != null && !b.getNotes().isBlank()) sb.append("- ").append(b.getNotes());
        return sb.toString().trim();
    }

    private String buildLaminationDescription(Lamination l) {
        StringBuilder sb = new StringBuilder("Lamination ");
        if (l.getQty() > 0) sb.append(l.getQty()).append(" ");
        if (l.getUnit() != null) sb.append(l.getUnit()).append(" ");
        if (l.getType() != null) sb.append(l.getType()).append(" ");
        if (l.getSide() != null) sb.append(l.getSide()).append(" ");
        if (l.getSize() != null) sb.append(l.getSize()).append(" ");
        if (l.getNotes() != null && !l.getNotes().isBlank()) sb.append("- ").append(l.getNotes());
        return sb.toString().trim();
    }

    private String buildCtpDescription(CtpPlate ctp) {
        StringBuilder sb = new StringBuilder("CTP Plate ");
        sb.append(ctp.getQty()).append(" pcs ");
        if (ctp.getPlateSize() != null) sb.append(ctp.getPlateSize()).append(" ");
        if (ctp.getGauge() != null) sb.append(ctp.getGauge()).append(" ");
        if (ctp.getBacking() != null) sb.append(ctp.getBacking()).append(" ");
        sb.append(ctp.getColor()).append(" ");
        if (ctp.getPlateSize() != null && !ctp.getNotes().isBlank()) sb.append("- ").append(ctp.getNotes());
        return sb.toString().trim();
    }
	
	
	
	
	
	
	
	
	
	
	
	
	
	//*****************************************OLD CODE*************************************

//	public JobItem addPrinting(int jobId, String qty, String units, String sets, String color, String side, String ctp,
//			String notes, String amountText) {
//
//		System.out.print("*******************JOB ID IS : " + jobId);
//		/* ========= VALIDATION ========= */
//
//		if (jobId <= 0)
//			throw new IllegalArgumentException("Job not created");
//
//		if (amountText == null || amountText.isBlank())
//			throw new IllegalArgumentException("Amount required");
//
//		double amount;
//		try {
//			amount = Double.parseDouble(amountText);
//		} catch (NumberFormatException e) {
//			throw new IllegalArgumentException("Invalid amount");
//		}
//
//		if (amount <= 0)
//			throw new IllegalArgumentException("Amount must be positive");
//
//		/* ========= DESCRIPTION BUILD ========= */
//
//		StringBuilder desc = new StringBuilder();
//
//		if (qty != null && units != null)
//			desc.append(qty).append(" ").append(units);
//
//		if (sets != null && !sets.isBlank())
//			desc.append(" - ").append(sets).append(" sets");
//
//		if (color != null)
//			desc.append(" - ").append(color);
//
//		if (side != null)
//			desc.append(" - ").append(side);
//
//		/* ========= MODEL ========= */
//
//		JobItem item = new JobItem();
//		item.setJobId(jobId);
//		item.setType("PRINTING");
//		item.setDescription(desc.toString());
//		item.setAmount(amount);
//		item.setSortOrder(1);
//
//		/* ========= SAVE ========= */
//
//		return repo.save(item);
//
//	}
//
//	public void addCtpItem(int jobId, String qty, String size, String gauge, String backing, String notes,
//			String amount, Supplier supplier, String ctpColor) {
//
//		if (jobId <= 0) {
//			throw new IllegalArgumentException("Job not created");
//		}
//
//		// 2️⃣ Build JOB_ITEM description
//		StringBuilder desc = new StringBuilder();
//
//		desc.append("CTP Plate ");
//		desc.append(qty).append(" pcs");
//
//		if (size != null)
//			desc.append(", Size ").append(size);
//
//		if (gauge != null)
//			desc.append(", Gauge ").append(gauge);
//
//		if (backing != null)
//			desc.append(", ").append(backing);
//
//		if (notes != null && !notes.isBlank())
//			desc.append(" (").append(notes).append(")");
//
//		/* ========= MODEL ========= */
//
//		JobItem item = new JobItem();
//		item.setJobId(jobId);
//		item.setType("CTP");
//		item.setDescription(desc.toString());
//		item.setAmount(Double.valueOf(amount));
//		item.setSortOrder(1);
//
//		repo.save(item);
//
//		/* =========CTP MODEL=========== */
//		CtpPlate ctp = new CtpPlate();
//		ctp.setAmount(Double.valueOf(amount));
//		ctp.setBacking(backing);
//		ctp.setGauge(gauge);
//		ctp.setJobId(jobId);
//		ctp.setNotes(notes);
//		ctp.setQty(Integer.valueOf(qty));
//		ctp.setSize(size);
//		ctp.setSupplierId(supplier.getId());
//		ctp.setSupplierNameSnapshot(supplier.getName());
//		ctp.setColor(ctpColor);
//		ctpRepo.save(ctp);
//
//	}
//
//	public void addPaper(int jobId, String qty, String unit, String size, String gsm, String type, String notes,
//			String amount, String source) {
//
//		if (jobId <= 0) {
//			throw new IllegalArgumentException("Job not created");
//		}
//
//		// 2️⃣ Build JOB_ITEM description
//		StringBuilder desc = new StringBuilder();
//
//		desc.append("Paper ");
//
//		desc.append(qty).append(" ").append(unit);
//
//		if (size != null)
//			desc.append(", ").append(size);
//
//		if (gsm != null)
//			desc.append(", ").append(gsm).append(" GSM");
//
//		if (type != null)
//			desc.append(", ").append(type);
//
//		if (source != null)
//			desc.append(" (").append(source).append(")");
//
//		if (notes != null && notes.isBlank())
//			desc.append(" - ").append(notes);
//
//		// 3️⃣ Save job item
//		JobItem ji = new JobItem();
//		ji.setJobId(jobId);
//		ji.setType("PAPER");
//		ji.setDescription(desc.toString());
//		ji.setAmount(Double.valueOf(amount));
//
//		repo.save(ji);
//	}
//
//	public void addBinding(int jobId, String binding_type, String qty, String rate, String notes, String amount) {
//
//		if (jobId <= 0) {
//			throw new IllegalArgumentException("Job not created");
//		}
//
//		// 2️⃣ Build JOB_ITEM description
//		StringBuilder desc = new StringBuilder();
//
//		desc.append("Binding ");
//
//		desc.append(qty).append(" ");
//
//		if (binding_type != null)
//			desc.append(binding_type).append(" ");
//
//		if (rate != null)
//			desc.append("@").append(rate);
//
//		if (notes != null && notes.isBlank())
//			desc.append(" - ").append(notes);
//
//		// 3️⃣ Save job item
//		JobItem ji = new JobItem();
//		ji.setJobId(jobId);
//		ji.setType("BINDING");
//		ji.setDescription(desc.toString());
//		ji.setAmount(Double.valueOf(amount));
//
//		repo.save(ji);
//	}
//
//	public void addLamination(int jobId, String qty, String unit, String type, String side, String size, String notes,
//			String amount) {
//
//		if (jobId <= 0) {
//			throw new IllegalArgumentException("Job not created");
//		}
//
//		// 2️⃣ Build JOB_ITEM description
//		StringBuilder desc = new StringBuilder();
//
//		desc.append("Lamination ");
//
//		if (qty != null && unit != null)
//			desc.append(qty).append(" ").append(unit).append(" ");
//
//		if (type != null)
//			desc.append(type).append(" ");
//
//		if (side != null)
//			desc.append(side).append(" ");
//		if (size != null)
//			desc.append(size).append(" ");
//
//		if (notes != null && notes.isBlank())
//			desc.append(" - ").append(notes);
//
//		// 3️⃣ Save job item
//		JobItem ji = new JobItem();
//		ji.setJobId(jobId);
//		ji.setType("Lamination");
//		ji.setDescription(desc.toString());
//		ji.setAmount(Double.valueOf(amount));
//
//		repo.save(ji);
//	}

}
