package service;

import model.CtpPlate;
import model.JobItem;
import model.Supplier;
import repository.CtpItemRepository;
import repository.JobItemRepository;

public class JobItemService {

	private final JobItemRepository repo = new JobItemRepository();
	private final CtpItemRepository ctpRepo = new CtpItemRepository();
	private final InvoiceGenerationService invoiceService = new InvoiceGenerationService();

	public JobItem addPrinting(int jobId, String qty, String units, String sets, String color, String side, String ctp,
			String notes, String amountText) {

		System.out.print("*******************JOB ID IS : " + jobId);
		/* ========= VALIDATION ========= */

		if (jobId <= 0)
			throw new IllegalArgumentException("Job not created");

		if (amountText == null || amountText.isBlank())
			throw new IllegalArgumentException("Amount required");

		double amount;
		try {
			amount = Double.parseDouble(amountText);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid amount");
		}

		if (amount <= 0)
			throw new IllegalArgumentException("Amount must be positive");

		/* ========= DESCRIPTION BUILD ========= */

		StringBuilder desc = new StringBuilder();

		if (qty != null && units != null)
			desc.append(qty).append(" ").append(units);

		if (sets != null && !sets.isBlank())
			desc.append(" - ").append(sets).append(" sets");

		if (color != null)
			desc.append(" - ").append(color);

		if (side != null)
			desc.append(" - ").append(side);

		/* ========= MODEL ========= */

		JobItem item = new JobItem();
		item.setJobId(jobId);
		item.setType("PRINTING");
		item.setDescription(desc.toString());
		item.setAmount(amount);
		item.setSortOrder(1);

		/* ========= SAVE ========= */

		return repo.save(item);

	}

	public void addCtpItem(int jobId, String qty, String size, String gauge, String backing, String notes,
			String amount, Supplier supplier, String ctpColor) {

		if (jobId <= 0) {
			throw new IllegalArgumentException("Job not created");
		}

		// 2️⃣ Build JOB_ITEM description
		StringBuilder desc = new StringBuilder();

		desc.append("CTP Plate ");
		desc.append(qty).append(" pcs");

		if (size != null)
			desc.append(", Size ").append(size);

		if (gauge != null)
			desc.append(", Gauge ").append(gauge);

		if (backing != null)
			desc.append(", ").append(backing);

		if (notes != null && !notes.isBlank())
			desc.append(" (").append(notes).append(")");

		/* ========= MODEL ========= */

		JobItem item = new JobItem();
		item.setJobId(jobId);
		item.setType("CTP");
		item.setDescription(desc.toString());
		item.setAmount(Double.valueOf(amount));
		item.setSortOrder(1);

		repo.save(item);

		/* =========CTP MODEL=========== */
		CtpPlate ctp = new CtpPlate();
		ctp.setAmount(Double.valueOf(amount));
		ctp.setBacking(backing);
		ctp.setGauge(gauge);
		ctp.setJobId(jobId);
		ctp.setNotes(notes);
		ctp.setQty(Integer.valueOf(qty));
		ctp.setSize(size);
		ctp.setSupplierId(supplier.getId());
		ctp.setSupplierNameSnapshot(supplier.getName());
		ctp.setColor(ctpColor);
		ctpRepo.save(ctp);

	}

	public void addPaper(int jobId, String qty, String unit, String size, String gsm, String type, String notes,
			String amount, String source) {

		if (jobId <= 0) {
			throw new IllegalArgumentException("Job not created");
		}

		// 2️⃣ Build JOB_ITEM description
		StringBuilder desc = new StringBuilder();

		desc.append("Paper ");

		desc.append(qty).append(" ").append(unit);

		if (size != null)
			desc.append(", ").append(size);

		if (gsm != null)
			desc.append(", ").append(gsm).append(" GSM");

		if (type != null)
			desc.append(", ").append(type);

		if (source != null)
			desc.append(" (").append(source).append(")");

		if (notes != null && notes.isBlank())
			desc.append(" - ").append(notes);

		// 3️⃣ Save job item
		JobItem ji = new JobItem();
		ji.setJobId(jobId);
		ji.setType("PAPER");
		ji.setDescription(desc.toString());
		ji.setAmount(Double.valueOf(amount));

		repo.save(ji);
	}

	public void addBinding(int jobId, String binding_type, String qty, String rate, String notes, String amount) {

		if (jobId <= 0) {
			throw new IllegalArgumentException("Job not created");
		}

		// 2️⃣ Build JOB_ITEM description
		StringBuilder desc = new StringBuilder();

		desc.append("Binding ");

		desc.append(qty).append(" ");

		if (binding_type != null)
			desc.append(binding_type).append(" ");

		if (rate != null)
			desc.append("@").append(rate);

		if (notes != null && notes.isBlank())
			desc.append(" - ").append(notes);

		// 3️⃣ Save job item
		JobItem ji = new JobItem();
		ji.setJobId(jobId);
		ji.setType("BINDING");
		ji.setDescription(desc.toString());
		ji.setAmount(Double.valueOf(amount));

		repo.save(ji);
	}

	public void addLamination(int jobId, String qty, String unit, String type, String side, String size, String notes,
			String amount) {

		if (jobId <= 0) {
			throw new IllegalArgumentException("Job not created");
		}

		// 2️⃣ Build JOB_ITEM description
		StringBuilder desc = new StringBuilder();

		desc.append("Lamination ");

		if (qty != null && unit != null)
			desc.append(qty).append(" ").append(unit).append(" ");

		if (type != null)
			desc.append(type).append(" ");

		if (side != null)
			desc.append(side).append(" ");
		if (size != null)
			desc.append(size).append(" ");

		if (notes != null && notes.isBlank())
			desc.append(" - ").append(notes);

		// 3️⃣ Save job item
		JobItem ji = new JobItem();
		ji.setJobId(jobId);
		ji.setType("Lamination");
		ji.setDescription(desc.toString());
		ji.setAmount(Double.valueOf(amount));

		repo.save(ji);
	}

}
