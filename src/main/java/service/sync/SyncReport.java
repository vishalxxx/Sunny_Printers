package service.sync;

public final class SyncReport {

	public int tempCodesPromoted;
	public int numberSequencesSynced;
	public int clientsSynced;
	public int jobsSynced;
	public int invoicesSynced;
	public int paymentsSynced;
	public int othersSynced;
	public int failures;
	public int pendingRemaining;

	public int totalSynced() {
		return tempCodesPromoted + numberSequencesSynced + clientsSynced + jobsSynced
				+ invoicesSynced + paymentsSynced + othersSynced;
	}

	@Override
	public String toString() {
		return "promoted=" + tempCodesPromoted + ", sequences=" + numberSequencesSynced
				+ ", clients=" + clientsSynced + ", jobs=" + jobsSynced
				+ ", invoices=" + invoicesSynced + ", payments=" + paymentsSynced
				+ ", others=" + othersSynced + ", failures=" + failures
				+ ", stillPending=" + pendingRemaining;
	}
}