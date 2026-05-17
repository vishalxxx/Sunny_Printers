package service.sync;

/** Shared SQL predicates for rows awaiting Supabase push. */
public final class PendingSyncFilters {

	public static final String PENDING_STATUS =
			"UPPER(TRIM(COALESCE(sync_status, ''))) IN ('', 'PENDING')";

	public static final String NOT_DELETED = "IFNULL(is_deleted, 0) = 0";

	private PendingSyncFilters() {
	}
}
