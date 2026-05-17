package api.supabase.sequences;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseRestClient;
import model.NumberSequence;
import repository.NumberSequenceRepository;
import utils.DBConnection;

public final class NumberSequenceSupabaseSync {

	private NumberSequenceSupabaseSync() {
	}

	public static void syncLocalToRemoteIfChangedAsync() {
		SupabaseGate.restClientIfConfigured().ifPresent(http -> CompletableFuture.runAsync(() -> {
			try {
				syncLocalToRemoteIfChanged(http);
			} catch (Exception ex) {
				System.err.println("[Supabase number_sequences] sync failed: " + ex.getMessage());
			}
		}));
	}

	public static void forceSyncLocalToRemoteAsync() {
		SupabaseGate.restClientIfConfigured().ifPresent(http -> CompletableFuture.runAsync(() -> {
			try {
				List<NumberSequence> local;
				try (Connection con = DBConnection.getConnection()) {
					local = new NumberSequenceRepository().findAll(con);
				}
				if (!local.isEmpty()) {
					NumberSequencesSupabaseApi api = new NumberSequencesSupabaseApi(http);
					api.upsertAll(local);
					System.out.println("[Supabase number_sequences] force-synced all " + local.size() + " row(s) to remote");
				}
			} catch (Exception ex) {
				System.err.println("[Supabase number_sequences] force sync failed: " + ex.getMessage());
			}
		}));
	}

	/** @return count of rows pushed to Supabase */
	public static int syncLocalToRemoteIfChanged(SupabaseRestClient http) throws Exception {
		List<NumberSequence> local;
		try (Connection con = DBConnection.getConnection()) {
			local = new NumberSequenceRepository().findAll(con);
		}
		if (local.isEmpty()) {
			return 0;
		}
		NumberSequencesSupabaseApi api = new NumberSequencesSupabaseApi(http);
		List<NumberSequence> remote;
		try {
			remote = api.listAll();
		} catch (Exception fetchEx) {
			String msg = fetchEx.getMessage() == null ? "" : fetchEx.getMessage();
			if (isLikelyOfflineOrTimeout(msg, fetchEx)) {
				System.err.println("[Supabase number_sequences] remote read skipped (offline/unreachable): "
						+ msg);
				return 0;
			}
			System.err.println("[Supabase number_sequences] remote read failed, pushing all local rows: "
					+ msg);
			api.upsertAll(local);
			return local.size();
		}
		Map<String, NumberSequence> remoteByKey = NumberSequencesSupabaseApi.indexByKey(remote);
		List<NumberSequence> changed = NumberSequencesSupabaseApi.findChanged(local, remoteByKey);
		if (changed.isEmpty()) {
			return 0;
		}
		api.upsertAll(changed);
		System.out.println("[Supabase number_sequences] synced " + changed.size() + " changed row(s)");
		return changed.size();
	}

	public static void pushAllAsync(List<NumberSequence> rows) {
		syncLocalToRemoteIfChangedAsync();
	}

	private static boolean isLikelyOfflineOrTimeout(String msg, Exception ex) {
		if (ex instanceof java.io.IOException || ex instanceof InterruptedException) {
			return true;
		}
		String lower = msg.toLowerCase();
		return lower.contains("timed out")
				|| lower.contains("timeout")
				|| lower.contains("connect")
				|| lower.contains("unreachable")
				|| lower.contains("no route")
				|| lower.contains("network");
	}
}