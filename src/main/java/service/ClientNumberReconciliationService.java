package service;

/**
 * @deprecated Use {@link service.sync.UniversalSyncEngine} and
 *             {@link service.sync.TemporaryDocumentReconciliation} instead.
 */
@Deprecated
public final class ClientNumberReconciliationService {

	private ClientNumberReconciliationService() {
	}

	public static void scheduleReconcileAsync() {
		service.sync.UniversalSyncEngine.scheduleSyncAsync();
	}

	public static int reconcilePendingClients() {
		return service.sync.TemporaryDocumentReconciliation.reconcileAll();
	}
}