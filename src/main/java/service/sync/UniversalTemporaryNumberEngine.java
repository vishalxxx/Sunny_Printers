package service.sync;

import java.sql.Connection;

import api.supabase.SupabaseGate;
import model.NumberSequence;
import repository.NumberSequenceRepository;
import service.NumberSequenceAllocationService;
import utils.DocumentNumbering;
import utils.NumberSequenceCatalog;

/**
 * Single entry point for offline / unreachable-API document numbers ({@code TEMP-*}).
 * Advances {@code number_sequences.offline_current_number} locally only.
 */
public final class UniversalTemporaryNumberEngine {

	private static final UniversalTemporaryNumberEngine INSTANCE = new UniversalTemporaryNumberEngine();

	private final NumberSequenceRepository numberSeqRepo = new NumberSequenceRepository();

	private UniversalTemporaryNumberEngine() {
	}

	public static UniversalTemporaryNumberEngine getInstance() {
		return INSTANCE;
	}

	public boolean isSupabaseConfigured() {
		return SupabaseGate.restClientIfConfigured().isPresent();
	}

	public boolean isRemoteSequenceReachable(String sequenceKey) {
		if (sequenceKey == null || sequenceKey.isBlank()) {
			return false;
		}
		return new NumberSequenceAllocationService().canReachSupabaseFor(sequenceKey.trim());
	}

	public String generate(Connection con, String sequenceKey) {
		if (sequenceKey == null || sequenceKey.isBlank()) {
			throw new IllegalArgumentException("sequenceKey is required");
		}
		String key = sequenceKey.trim();
		try {
			NumberSequence local = numberSeqRepo.findByKey(con, key);
			if (local == null) {
				return fallbackFromKey(key);
			}
			long next = numberSeqRepo.nextOfflineNumber(con, key);
			int pad = Math.max(1, local.getDigitWidth() > 0 ? local.getDigitWidth() : 4);
			String formatted = DocumentNumbering.formatTemporary(local.getPrefix(), next, pad);
			System.out.println("[UniversalTemporaryNumberEngine] " + key + ": " + formatted);
			return formatted;
		} catch (Exception e) {
			System.err.println("[UniversalTemporaryNumberEngine] " + key + " failed: " + e.getMessage());
			return "TEMP-" + System.currentTimeMillis();
		}
	}

	public NumberSequenceAllocationService.AllocatedNumber allocateTemporary(Connection con, String sequenceKey) {
		return new NumberSequenceAllocationService.AllocatedNumber(generate(con, sequenceKey), true);
	}

	private static String fallbackFromKey(String key) {
		String prefix = key.toUpperCase().replace("_", "");
		if (prefix.length() > 4) {
			prefix = prefix.substring(0, 4);
		}
		return "TEMP-" + prefix + "-" + (System.currentTimeMillis() % 100_000);
	}
}