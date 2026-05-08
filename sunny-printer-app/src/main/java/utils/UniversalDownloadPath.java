package utils;

import java.io.File;
import java.util.prefs.Preferences;

import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/**
 * User-configurable default folder (General Settings) used as the initial directory for file pickers.
 */
public final class UniversalDownloadPath {

	private static final Preferences PREFS = Preferences.userRoot().node("sunny_printers");
	private static final String KEY = "universal_download_path";

	private UniversalDownloadPath() {
	}

	public static String get() {
		String v = PREFS.get(KEY, "");
		return v != null ? v.trim() : "";
	}

	public static void set(String path) {
		if (path == null || path.isBlank()) {
			PREFS.remove(KEY);
		} else {
			PREFS.put(KEY, path.trim());
		}
	}

	/**
	 * Existing directory to use as FileChooser / DirectoryChooser starting folder, or null.
	 */
	public static File resolveInitialDirectory() {
		String p = get();
		if (p.isEmpty()) {
			return null;
		}
		File f = new File(p);
		if (f.isDirectory()) {
			return f;
		}
		if (f.getParentFile() != null && f.getParentFile().isDirectory()) {
			return f.getParentFile();
		}
		return null;
	}

	public static void prepareFileChooser(FileChooser chooser) {
		if (chooser == null) {
			return;
		}
		File dir = resolveInitialDirectory();
		if (dir != null) {
			chooser.setInitialDirectory(dir);
		}
	}

	public static void prepareDirectoryChooser(DirectoryChooser chooser) {
		if (chooser == null) {
			return;
		}
		File dir = resolveInitialDirectory();
		if (dir != null) {
			chooser.setInitialDirectory(dir);
		}
	}
}
