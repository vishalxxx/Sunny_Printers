package utils;

import java.util.List;
import java.util.Locale;

public final class JobWorkflow {

	private JobWorkflow() {
	}

	public enum Major {
		DRAFT, PROCESSING, COMPLETED, INVOICE, CANCELLED
	}

	public static Major majorFromJobStatus(String status) {
		String s = status == null ? "" : status.toLowerCase(Locale.ROOT).replace('_', ' ');
		if (s.contains("cancel")) {
			return Major.CANCELLED;
		}
		if (s.contains("invoice") || s.contains("invoic")) {
			return Major.INVOICE;
		}
		if (s.contains("completed")) {
			return Major.COMPLETED;
		}
		if (s.contains("progress")) {
			return Major.PROCESSING;
		}
		if (s.contains("draft") || s.contains("created")) {
			return Major.DRAFT;
		}
		return Major.DRAFT;
	}

	public static List<String> childSteps(Major major) {
		return switch (major) {
			case DRAFT -> List.of(
					"Waiting Approval", "Client Discussion", "Quotation Sent", "Job Confirmed");
			case PROCESSING -> List.of(
					"Pre-Press", "CTP Ready", "Printing", "Lamination", "Binding", "QC Check");
			case COMPLETED -> List.of(
					"Packed", "Ready for Pickup", "Courier Assigned", "Delivered");
			case INVOICE -> List.of(
					"Invoice Drafted", "Invoice Sent", "Partial Payment", "Paid");
			case CANCELLED -> List.of();
		};
	}

	public static String defaultChildForMajor(Major major) {
		List<String> steps = childSteps(major);
		return steps.isEmpty() ? "" : steps.get(0);
	}

	public static boolean isValidChildForMajor(Major major, String child) {
		if (child == null || child.isBlank() || major == Major.CANCELLED) {
			return false;
		}
		String c = child.trim();
		return childSteps(major).stream().anyMatch(step -> step.equalsIgnoreCase(c));
	}

	public static String canonicalChildLabel(Major major, String child) {
		if (child == null || major == Major.CANCELLED) {
			return "";
		}
		String c = child.trim();
		for (String step : childSteps(major)) {
			if (step.equalsIgnoreCase(c)) {
				return step;
			}
		}
		return c;
	}

	public static String resolveEffectiveChild(String jobStatus, String storedChild, Major major) {
		if (major == Major.CANCELLED) {
			return "";
		}
		List<String> steps = childSteps(major);
		if (steps.isEmpty()) {
			return "";
		}
		if (storedChild != null && !storedChild.isBlank()) {
			for (String step : steps) {
				if (step.equalsIgnoreCase(storedChild.trim())) {
					return step;
				}
			}
		}
		String s = jobStatus == null ? "" : jobStatus.toLowerCase(Locale.ROOT).replace('_', ' ');
		if (major == Major.INVOICE) {
			if (s.contains("invoice drafted") || s.contains("invoice_drafted")) {
				return "Invoice Drafted";
			}
			if (s.contains("partial")) {
				return "Partial Payment";
			}
			if (s.contains("invoiced") && !s.contains("draft")) {
				return "Paid";
			}
		}
		if (major == Major.COMPLETED && s.contains("ready")) {
			return "Ready for Pickup";
		}
		return steps.get(0);
	}

	public static int indexOfChild(Major major, String childLabel) {
		List<String> steps = childSteps(major);
		if (childLabel == null) {
			return -1;
		}
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).equalsIgnoreCase(childLabel.trim())) {
				return i;
			}
		}
		return -1;
	}
}