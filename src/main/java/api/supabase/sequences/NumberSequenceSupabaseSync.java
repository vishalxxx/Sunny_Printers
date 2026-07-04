package api.supabase.sequences;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseRestClient;
import model.NumberSequence;
import model.User;
import repository.NumberSequenceRepository;
import utils.DBConnection;
import utils.SessionManager;

public final class NumberSequenceSupabaseSync {

	private NumberSequenceSupabaseSync() {
	}

	public static void syncRemoteToLocalAsync() {
		SupabaseGate.restClientIfConfigured().ifPresent(http -> {
			Runnable task = () -> {
				try {
					syncRemoteToLocal(http);
				} catch (Exception ex) {
					System.err.println("[Supabase number_sequences] syncRemoteToLocal failed: " + ex.getMessage());
				}
			};
			if (SupabaseGate.isOverrideActive()) {
				utils.SQLiteWriteCoordinator.runAsBackground(task);
			} else {
				CompletableFuture.runAsync(() -> utils.SQLiteWriteCoordinator.runAsBackground(task));
			}
		});
	}

	public static int syncRemoteToLocal(SupabaseRestClient http) throws Exception {
		NumberSequencesSupabaseApi api = new NumberSequencesSupabaseApi(http);
		List<NumberSequence> remote = api.listAll();
		if (remote == null || remote.isEmpty()) {
			return 0;
		}
		NumberSequenceRepository repo = new NumberSequenceRepository();
		repository.SystemSettingsRepository settingsRepo = new repository.SystemSettingsRepository();
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				for (NumberSequence r : remote) {
					repo.upsert(con, r);
				}
				model.SystemSettings settings = settingsRepo.load(con);
				repo.applyToSystemSettings(con, settings);
				settingsRepo.save(con, settings);
				con.commit();
			} catch (Exception e) {
				con.rollback();
				throw e;
			}
		}
		System.out.println("[Supabase number_sequences] Synced " + remote.size() + " sequences from remote to local.");
		return remote.size();
	}

	public static void syncLocalToRemoteAsync() {
		User current = SessionManager.getInstance().getCurrentUser();
		boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());
		if (!isAdmin) {
			return; // Non-admins are not allowed to push number sequences
		}
		SupabaseGate.restClientIfConfigured().ifPresent(http -> {
			Runnable task = () -> {
				try {
					syncLocalToRemote(http);
				} catch (Exception ex) {
					System.err.println("[Supabase number_sequences] syncLocalToRemote failed: " + ex.getMessage());
				}
			};
			if (SupabaseGate.isOverrideActive()) {
				utils.SQLiteWriteCoordinator.runAsBackground(task);
			} else {
				CompletableFuture.runAsync(() -> utils.SQLiteWriteCoordinator.runAsBackground(task));
			}
		});
	}

	public static int syncLocalToRemote(SupabaseRestClient http) throws Exception {
		User current = SessionManager.getInstance().getCurrentUser();
		boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());
		if (!isAdmin) {
			throw new SecurityException("Only admin is allowed to push number sequences to Supabase.");
		}
		List<NumberSequence> local;
		try (Connection con = DBConnection.getConnection()) {
			local = new NumberSequenceRepository().findAll(con);
		}
		if (local.isEmpty()) {
			return 0;
		}
		NumberSequencesSupabaseApi api = new NumberSequencesSupabaseApi(http);
		api.upsertAll(local);
		System.out.println("[Supabase number_sequences] Pushed " + local.size() + " sequences from local to remote.");
		return local.size();
	}
}