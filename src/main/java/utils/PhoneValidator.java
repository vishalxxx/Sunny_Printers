package utils;

import java.util.regex.Pattern;

public final class PhoneValidator {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^[6-9][0-9]{9}$");
    private static final Pattern LANDLINE_PATTERN = Pattern.compile("^[0-9]{8,15}$");

    private PhoneValidator() {}

    /**
     * Sanitizes input by trimming leading/trailing whitespace.
     */
    public static String sanitize(String input) {
        if (input == null) return "";
        return input.trim();
    }

    /**
     * Validates standard 10-digit mobile phone number.
     * Allowed starting digits: 6, 7, 8, 9.
     */
    public static boolean isValidMobile(String mobile) {
        if (mobile == null) return false;
        String clean = sanitize(mobile);
        return MOBILE_PATTERN.matcher(clean).matches();
    }

    /**
     * Validates optional landline/alternate phone number.
     * Allowed length: 8 to 15 digits.
     */
    public static boolean isValidLandline(String landline) {
        if (landline == null || landline.trim().isEmpty()) return true; // Optional field
        String clean = sanitize(landline);
        return LANDLINE_PATTERN.matcher(clean).matches();
    }
}
