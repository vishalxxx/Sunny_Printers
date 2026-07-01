package service.sync;

/**
 * Strict push order for pending rows to Supabase (FK-safe).
 */
public enum SyncEntityPhase {
	CLIENT(1),
	JOB(2),
	INVOICE(3),
	PAYMENT(4),
	OTHER(5);

	private final int order;

	SyncEntityPhase(int order) {
		this.order = order;
	}

	public int order() {
		return order;
	}
}
