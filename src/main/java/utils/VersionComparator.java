package utils;

public class VersionComparator {

    public static int compare(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String[] parts1 = v1.trim().split("\\.");
        String[] parts2 = v2.trim().split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? parseSegment(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseSegment(parts2[i]) : 0;

            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
        }
        return 0;
    }

    private static int parseSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return 0;
        }
        // Extract digits prefix (handles suffixes like 1.0.0-beta or similar gracefully)
        StringBuilder digits = new StringBuilder();
        for (char c : segment.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        try {
            return digits.length() > 0 ? Integer.parseInt(digits.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
