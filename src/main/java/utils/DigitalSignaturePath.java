package utils;

import java.util.prefs.Preferences;

public final class DigitalSignaturePath {
	private static final Preferences PREFS = Preferences.userRoot().node("sunny_printers");
	private static final String KEY = "digital_signature_path";

	private DigitalSignaturePath() {
	}

	public static String get() {
		return PREFS.get(KEY, "");
	}

	public static void set(String path) {
		if (path == null || path.isBlank()) {
			PREFS.remove(KEY);
		} else {
			PREFS.put(KEY, path.trim());
		}
	}
}
