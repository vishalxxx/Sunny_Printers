package utils;

import java.text.DecimalFormat;

public class NumberToWords {

    private static final String[] tensNames = {
        "", " Ten", " Twenty", " Thirty", " Forty", " Fifty", " Sixty", " Seventy", " Eighty", " Ninety"
    };

    private static final String[] numNames = {
        "", " One", " Two", " Three", " Four", " Five", " Six", " Seven", " Eight", " Nine", " Ten",
        " Eleven", " Twelve", " Thirteen", " Fourteen", " Fifteen", " Sixteen", " Seventeen", " Eighteen", " Nineteen"
    };

    private NumberToWords() {}

    private static String convertLessThanOneThousand(int number) {
        String soFar;

        if (number % 100 < 20) {
            soFar = numNames[number % 100];
            number /= 100;
        } else {
            soFar = numNames[number % 10];
            number /= 10;

            soFar = tensNames[number % 10] + soFar;
            number /= 10;
        }
        if (number == 0) return soFar;
        return numNames[number] + " Hundred" + soFar;
    }

    public static String convert(long number) {
        if (number == 0) return "Zero";

        String snumber = Long.toString(number);

        // pad with "0"
        String mask = "000000000";
        DecimalFormat df = new DecimalFormat(mask);
        snumber = df.format(number);

        // XX | XX | XX | XXX
        // Cr | Lk | Th | Hnd
        int crores = Integer.parseInt(snumber.substring(0, 2));
        int lakhs = Integer.parseInt(snumber.substring(2, 4));
        int thousands = Integer.parseInt(snumber.substring(4, 6));
        int hundreds = Integer.parseInt(snumber.substring(6, 9));

        String result = "";

        if (crores > 0) {
            result += convertLessThanOneThousand(crores) + " Crore";
        }

        if (lakhs > 0) {
            result += convertLessThanOneThousand(lakhs) + " Lakh";
        }

        if (thousands > 0) {
            result += convertLessThanOneThousand(thousands) + " Thousand";
        }

        if (hundreds > 0) {
            result += convertLessThanOneThousand(hundreds);
        }

        return result.trim();
    }

    public static String convertToIndianCurrency(double amount) {
        long wholePart = (long) amount;
        int paisaPart = (int) Math.round((amount - wholePart) * 100);

        String result = "INR " + convert(wholePart) + " Only";
        if (paisaPart > 0) {
            result = "INR " + convert(wholePart) + " and " + convert(paisaPart) + " Paise Only";
        }
        return result;
    }
}
