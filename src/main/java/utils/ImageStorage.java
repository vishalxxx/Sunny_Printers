package utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class ImageStorage {

    private ImageStorage() {}

    /**
     * Returns the absolute path to the images directory in the user home directory.
     * e.g., ~/.sunnyprinters/Images
     */
    public static File getImagesDir() {
        File dir = new File(System.getProperty("user.home"), ".sunnyprinters/Images");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Resolves a stored image path (either absolute, relative, or old format)
     * to a valid, existing File object. Returns a File representing the expected path
     * in the user home directory if not found locally.
     */
    public static File resolveImageFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        
        // 1. Try resolving directly as is
        File f = new File(path);
        if (f.exists()) {
            return f;
        }

        // 2. If it's a relative path starting with Images, resolve to user home
        String normalized = path.replace("\\", "/");
        if (normalized.startsWith("Images/")) {
            String filename = normalized.substring("Images/".length());
            File homeFile = new File(getImagesDir(), filename);
            if (homeFile.exists()) {
                return homeFile;
            }
            // Return homeFile even if it doesn't exist, as the canonical target location
            return homeFile;
        }

        return f;
    }

    /**
     * Copies a selected image file to the safe user home images directory,
     * and returns the database-friendly relative path representation (e.g. Images/filename).
     */
    public static String saveImage(File sourceFile, String jobUuid) throws Exception {
        if (sourceFile == null) {
            return null;
        }
        
        String ext = "";
        String name = sourceFile.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            ext = name.substring(dotIndex);
        }
        
        String newFileName = "job_" + jobUuid.replace("-", "") + "_" + System.currentTimeMillis() + ext;
        File targetFile = new File(getImagesDir(), newFileName);
        
        Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        return "Images/" + newFileName;
    }
}
