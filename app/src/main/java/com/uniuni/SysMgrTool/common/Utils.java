package com.uniuni.SysMgrTool.common;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.os.VibratorManager;


public class Utils {

    public static class AddressInfo {
        private final String apartmentNumber;
        private final String streetNumber;

        public AddressInfo(String apartmentNumber, String streetNumber) {
            this.apartmentNumber = apartmentNumber;
            this.streetNumber = streetNumber;
        }

        public String getApartmentNumber() {
            return apartmentNumber;
        }

        public String getStreetNumber() {
            return streetNumber;
        }
    }


    private static final Pattern APARTMENT_PATTERN = Pattern.compile("^(\\d+)-(\\d+)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("\\b\\d{1,3}\\s?\\w{1,2}\\s?\\d{1,3}\\b");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b[a-zA-Z]+\\b");


    public static AddressInfo extractApartmentAndStreetNumber(String strAddress) {
        if (strAddress == null || strAddress.isEmpty()) {
            return new AddressInfo("", "");
        }

        String address = strAddress.trim();

        Matcher apartmentMatcher = APARTMENT_PATTERN.matcher(address);
        if (apartmentMatcher.find()) {
            return new AddressInfo(apartmentMatcher.group(1), apartmentMatcher.group(2));
        }


        Matcher numberMatcher = NUMBER_PATTERN.matcher(address);
        String[] numbers = new String[2];
        int count = 0;
        int[] positions = new int[2]; // Store positions of the found numbers

        while (numberMatcher.find() && count < 2) {
            positions[count] = numberMatcher.start();
            numbers[count] = numberMatcher.group(1).trim();
            count++;
        }

        // Perform checks to ensure numbers are valid
        for (int i = 0; i < count; i++) {
            if (isAdjacentCharacterLetter(address, positions[i])) {
                numbers[i] = "";
            }
        }

        return new AddressInfo(numbers[1], numbers[0]);
    }

    private static boolean isAdjacentCharacterLetter(String address, int index) {

        if (index + 1 < address.length()) {
            char nextChar = address.charAt(index + 1);
            return Character.isLetter(nextChar);
        }
        return false;
    }

    public static String extractFirstWord(String address) {
        if (address == null || address.isEmpty()) {
            return "";
        }

        Matcher firstWordMatcher = WORD_PATTERN.matcher(address);
        if (firstWordMatcher.find()) {
            return firstWordMatcher.group();
        }

        return "";
    }

    // 获取 Vibrator 实例
    private static Vibrator getVibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For API 31 (Android S) and above
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            // For below API 31
            return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }


    public static void vibrate(Context context, long duration) {
        Vibrator vibrator = getVibrator(context);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For API 26 (Oreo) and above
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                // For below API 26
                vibrator.vibrate(duration);
            }
        }
    }

    public static void vibratePattern(Context context, long[] pattern, boolean repeat) {
        Vibrator vibrator = getVibrator(context);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For API 26 (Oreo) and above
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat ? 0 : -1));
            } else {
                // For below API 26
                vibrator.vibrate(pattern, repeat ? 0 : -1);
            }
        }
    }
}
