package utils;

import java.util.regex.Pattern;

public final class GSTINValidator {

    private static final String ALPHANUMERIC_CODE = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[0-9A-Z]{1}Z[0-9A-Z]{1}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$", Pattern.CASE_INSENSITIVE);

    public static final String[] ALL_STATES = {
        "Jammu & Kashmir (01)", "Himachal Pradesh (02)", "Punjab (03)", "Chandigarh (04)",
        "Uttarakhand (05)", "Haryana (06)", "Delhi (07)", "Rajasthan (08)", "Uttar Pradesh (09)",
        "Bihar (10)", "Sikkim (11)", "Arunachal Pradesh (12)", "Nagaland (13)", "Manipur (14)",
        "Mizoram (15)", "Tripura (16)", "Meghalaya (17)", "Assam (18)", "West Bengal (19)",
        "Jharkhand (20)", "Odisha (21)", "Chhattisgarh (22)", "Madhya Pradesh (23)", "Gujarat (24)",
        "Daman & Diu (25)", "Dadra & Nagar Haveli (26)", "Maharashtra (27)", "Karnataka (29)",
        "Goa (30)", "Lakshadweep (31)", "Kerala (32)", "Tamil Nadu (33)", "Puducherry (34)",
        "Andaman & Nicobar Islands (35)", "Telangana (36)", "Andhra Pradesh (New) (37)", "Ladakh (38)"
    };

    private GSTINValidator() {}

    /**
     * Complete check: format + state + PAN + checksum
     */
    public static boolean isValid(String gstin) {
        if (gstin == null) return false;
        String clean = gstin.trim().toUpperCase();
        return isFormatValid(clean) && isStateCodeValid(clean) && isPANValidFromGstin(clean) && isChecksumValid(clean);
    }

    public static boolean isFormatValid(String gstin) {
        if (gstin == null) return false;
        return GSTIN_PATTERN.matcher(gstin.trim()).matches();
    }

    public static boolean isStateCodeValid(String gstin) {
        if (gstin == null || gstin.trim().length() < 2) return false;
        String code = gstin.trim().substring(0, 2);
        return getStateByCode(code) != null;
    }

    public static boolean isPANValidFromGstin(String gstin) {
        if (gstin == null || gstin.trim().length() < 12) return false;
        String pan = gstin.trim().substring(2, 12);
        return PAN_PATTERN.matcher(pan).matches();
    }

    public static boolean isChecksumValid(String gstin) {
        if (gstin == null || gstin.trim().length() != 15) return false;
        String input = gstin.trim().toUpperCase();
        String mainPart = input.substring(0, 14);
        char providedChecksum = input.charAt(14);
        try {
            return calculateChecksum(mainPart) == providedChecksum;
        } catch (Exception e) {
            return false;
        }
    }

    public static char calculateChecksum(String input) {
        int factor = 1;
        int sum = 0;
        int mod = 36;

        for (int i = 0; i < input.length(); i++) {
            int codePoint = ALPHANUMERIC_CODE.indexOf(input.charAt(i));
            if (codePoint == -1) {
                throw new IllegalArgumentException("Invalid character: " + input.charAt(i));
            }
            int digit = factor * codePoint;
            factor = (factor == 2) ? 1 : 2;
            digit = (digit / mod) + (digit % mod);
            sum += digit;
        }

        int remainder = sum % mod;
        int checksumCode = (mod - remainder) % mod;
        return ALPHANUMERIC_CODE.charAt(checksumCode);
    }

    public static String getStateByCode(String code) {
        if (code == null || code.length() != 2) return null;
        for (String s : ALL_STATES) {
            if (s.contains("(" + code + ")")) {
                return s;
            }
        }
        return null;
    }

    public static String getRegistrationType(String gstin) {
        if (gstin == null || gstin.trim().length() < 14) return "Unknown";
        char entityCode = gstin.toUpperCase().charAt(12);
        char defaultZ = gstin.toUpperCase().charAt(13);
        if (defaultZ == 'Z') {
            return "Regular Taxpayer (Entity Code: " + entityCode + ")";
        } else {
            return "Special Taxpayer (Entity Code: " + entityCode + ")";
        }
    }
}

