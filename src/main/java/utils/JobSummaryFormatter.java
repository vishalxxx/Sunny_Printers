package utils;

import model.Binding;
import model.CtpPlate;
import model.Job;
import model.Lamination;
import model.Paper;
import model.Printing;

public class JobSummaryFormatter {

	public static String generateSummary(Job job) {
		StringBuilder sb = new StringBuilder();

		sb.append("\n============================\n");
		sb.append("        JOB SUMMARY\n");
		sb.append("============================\n\n");

		/* ---------------------------- PRINTING ---------------------------- */
		if (!job.getPrintingList().isEmpty()) {
			sb.append("🖨 PRINTING\n");
			for (Printing p : job.getPrintingList()) {

				sb.append("• ").append(p.getQty()).append(" ").append(nullSafe(p.getUnits())).append(" | Set: ")
						.append(nullSafe(p.getSet())).append(" | ").append(nullSafe(p.getColor())).append(" | ")
						.append(nullSafe(p.getSide())).append(" | CTP: ").append(nullSafe(p.getWithCtp())).append("\n");

				if (notEmpty(p.getNotes())) {
					sb.append("    Notes: ").append(p.getNotes()).append("\n");
				}

				if (notEmpty(p.getAmount())) {
					sb.append("    Amount: ₹").append(p.getAmount()).append("\n");
				}
				sb.append("\n");
			}
		}

		/* ---------------------------- CTP PLATE ---------------------------- */
		if (!job.getCtpPlateList().isEmpty()) {
			sb.append("📄 CTP PLATES\n");
			for (CtpPlate c : job.getCtpPlateList()) {

				sb.append("• ").append(c.getQty()).append(" Plate(s)").append(" | Size: ").append(nullSafe(c.getSize()))
						.append(" | Gauge: ").append(nullSafe(c.getGauge())).append(" | Backing: ")
						.append(nullSafe(c.getBacking())).append("\n");

				if (notEmpty(c.getNotes())) {
					sb.append("    Notes: ").append(c.getNotes()).append("\n");
				}

				if (notEmpty(c.getAmount())) {
					sb.append("    Amount: ₹").append(c.getAmount()).append("\n");
				}
				sb.append("\n");
			}
		}

		/* ---------------------------- PAPER ---------------------------- */
		if (!job.getPaperList().isEmpty()) {
			sb.append("📦 PAPER\n");
			for (Paper p : job.getPaperList()) {

				sb.append("• ").append(p.getQty()).append(" ").append(nullSafe(p.getUnits())).append(" | Size: ")
						.append(nullSafe(p.getSize())).append(" | GSM: ").append(nullSafe(p.getGsm()))
						.append(" | Type: ").append(nullSafe(p.getType())).append("\n");

				if (notEmpty(p.getNotes())) {
					sb.append("    Notes: ").append(p.getNotes()).append("\n");
				}

				if (notEmpty(p.getAmount())) {
					sb.append("    Amount: ₹").append(p.getAmount()).append("\n");
				}
				sb.append("\n");
			}
		}

		/* ---------------------------- BINDING ---------------------------- */
		if (!job.getBindingList().isEmpty()) {
			sb.append("📚 BINDING\n");
			for (Binding b : job.getBindingList()) {

				sb.append("• ").append(nullSafe(b.getProcess())).append(" | Qty: ").append(nullSafe(b.getQty()))
						.append(" | Rate: ₹").append(nullSafe(b.getRate())).append("\n");

				if (notEmpty(b.getNotes())) {
					sb.append("    Notes: ").append(b.getNotes()).append("\n");
				}

				if (notEmpty(b.getAmount())) {
					sb.append("    Amount: ₹").append(b.getAmount()).append("\n");
				}
				sb.append("\n");
			}
		}

		/* ---------------------------- LAMINATION ---------------------------- */
		if (!job.getLaminationList().isEmpty()) {
			sb.append("🎞 LAMINATION\n");
			for (Lamination l : job.getLaminationList()) {

				sb.append("• ").append(l.getQty()).append(" ").append(nullSafe(l.getUnit())).append(" | Type: ")
						.append(nullSafe(l.getType())).append(" | Side: ").append(nullSafe(l.getSide()))
						.append(" | Size: ").append(nullSafe(l.getSize())).append("\n");

				if (notEmpty(l.getNotes())) {
					sb.append("    Notes: ").append(l.getNotes()).append("\n");
				}

				if (notEmpty(l.getAmount())) {
					sb.append("    Amount: ₹").append(l.getAmount()).append("\n");
				}
				sb.append("\n");
			}
		}

		return sb.toString();
	}

	/* ------------------ Helper Functions ------------------ */

	private static String nullSafe(String v) {
		return v == null ? "-" : v;
	}

	private static boolean notEmpty(String v) {
		return v != null && !v.trim().isEmpty();
	}
}
