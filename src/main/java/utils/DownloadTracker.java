package utils;

import java.io.File;
import java.util.Locale;

import controller.MainController;
import javafx.application.Platform;

/**
 * Registers saved/export files so they appear in the dashboard downloads popup.
 */
public final class DownloadTracker {

	private DownloadTracker() {
	}

	public static void registerExportedFile(File file) {
		registerExportedFile(file, null);
	}

	public static void registerExportedFile(File file, String typeOverride) {
		if (file == null || !file.isFile()) {
			return;
		}
		final String name = file.getName();
		final String type = typeOverride != null && !typeOverride.isBlank()
				? typeOverride.trim()
				: fileTypeFromFileName(name);
		final String size = formatBytes(file.length());
		final String path = file.getAbsolutePath();
		Runnable action = () -> {
			MainController mc = MainController.getInstance();
			if (mc != null) {
				mc.addDownload(name, type, size, path);
			}
		};
		if (Platform.isFxApplicationThread()) {
			action.run();
		} else {
			Platform.runLater(action);
		}
	}

	private static String fileTypeFromFileName(String name) {
		if (name == null) {
			return "FILE";
		}
		String n = name.toLowerCase(Locale.ROOT);
		if (n.endsWith(".pdf")) {
			return "PDF";
		}
		if (n.endsWith(".xlsx") || n.endsWith(".xls")) {
			return "EXCEL";
		}
		if (n.endsWith(".csv")) {
			return "CSV";
		}
		if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
			return "IMAGE";
		}
		return "FILE";
	}

	private static String formatBytes(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		double kb = bytes / 1024.0;
		if (kb < 1024) {
			return String.format(Locale.ROOT, "%.1f KB", kb);
		}
		double mb = kb / 1024.0;
		if (mb < 1024) {
			return String.format(Locale.ROOT, "%.1f MB", mb);
		}
		return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
	}
}
