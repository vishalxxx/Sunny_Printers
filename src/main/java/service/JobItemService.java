package service;

import model.JobItem;
import repository.JobItemRepository;

public class JobItemService {

	private final JobItemRepository repo = new JobItemRepository();

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
}
