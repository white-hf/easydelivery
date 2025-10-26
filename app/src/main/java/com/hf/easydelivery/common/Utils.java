package com.hf.easydelivery.common;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.os.VibratorManager;
import android.util.Log;
import android.widget.Toast;

import com.hf.easydelivery.Constants;
import com.hf.easydelivery.MyApplication;
import com.hf.easydelivery.ResourceMgr;


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

    private static final Pattern LEADING_UNIT_HYPHEN = Pattern.compile("^\\s*(\\w{1,6})\\s*-\\s*(\\d{1,5})\\b");
    private static final Pattern UNIT_PREFIX_PATTERN = Pattern.compile(
            "^\\s*(?:apt|apartment|unit|suite|ste|rm|room|ph|buzzer|fl|floor|lvl|level|entrance|door|code|bldg|building|#)\\s*[:#-]?\\s*(\\w{1,6})\\s+(\\d{1,5})\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DOUBLE_NUMBER_PREFIX = Pattern.compile("^\\s*(\\d{1,4})\\s+(\\d{1,5})\\b");
    private static final Pattern HASH_ONLY_PREFIX = Pattern.compile("^\\s*#\\s*(\\w{1,6})\\b");
    private static final Pattern GENERIC_NUMBER_PATTERN = Pattern.compile("\\b(\\d{1,5}[A-Za-z]?)\\b");
    private static final Pattern UNIT_KEYWORD_GLOBAL = Pattern.compile(
            "(?i)(?:\\b(?:apt|apartment|unit|suite|ste|rm|room|ph|buzzer|fl|floor|lvl|level|entrance|door|code|bldg|building|locker|buzz)\\s*[:#-]?\\s*(\\w{1,6}))"
    );
    private static final Pattern TRAILING_UNIT_PATTERN = Pattern.compile("(?i)(?:#|no\\.?|unit)\\s*(\\w{1,6})\\s*$");


    public String getTodayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());
    }

    public static AddressInfo extractApartmentAndStreetNumber(String strAddress) {
        if (strAddress == null || strAddress.trim().isEmpty()) {
            return new AddressInfo("", "");
        }

        String normalized = normalizeAddress(strAddress);
        UnitExtractionResult leading = extractLeadingUnit(normalized);
        String working = leading.remaining;

        String streetNumber = firstStreetNumber(working);
        if (streetNumber.isEmpty()) {
            streetNumber = firstStreetNumber(normalized);
        }

        String apartment = leading.unit;
        if (apartment.isEmpty()) {
            apartment = detectUnitFromKeywords(normalized, streetNumber);
        }
        if (apartment.isEmpty()) {
            apartment = heuristicUnitFromNumbers(normalized, streetNumber);
        }

        return new AddressInfo(apartment, streetNumber);
    }

    private static String normalizeAddress(String raw) {
        String stripped = stripNonAddressPrefix(raw == null ? "" : raw);
        stripped = stripped.replace(',', ' ');
        stripped = stripped.replaceAll("\\s+", " ").trim();
        return stripped;
    }

    private static String stripNonAddressPrefix(String raw) {
        if (raw == null) return "";
        Matcher digit = Pattern.compile("\\d").matcher(raw);
        if (!digit.find()) {
            return raw.trim();
        }
        int idx = digit.start();
        String prefix = raw.substring(0, idx);
        if (prefix.trim().isEmpty() || !prefix.contains(" ")) {
            return raw.substring(idx).trim();
        }
        return raw.trim();
    }

    private static UnitExtractionResult extractLeadingUnit(String address) {
        String working = address;

        Matcher hyphen = LEADING_UNIT_HYPHEN.matcher(working);
        if (hyphen.find()) {
            return new UnitExtractionResult(hyphen.group(1), working.substring(hyphen.start(2)).trim());
        }

        Matcher prefix = UNIT_PREFIX_PATTERN.matcher(working);
        if (prefix.find()) {
            return new UnitExtractionResult(prefix.group(1), working.substring(prefix.start(2)).trim());
        }

        Matcher doubleNumbers = DOUBLE_NUMBER_PREFIX.matcher(working);
        if (doubleNumbers.find()) {
            return new UnitExtractionResult(doubleNumbers.group(1), working.substring(doubleNumbers.start(2)).trim());
        }

        Matcher hashOnly = HASH_ONLY_PREFIX.matcher(working);
        if (hashOnly.find()) {
            return new UnitExtractionResult(hashOnly.group(1), working.substring(hashOnly.end()).trim());
        }

        return new UnitExtractionResult("", working);
    }

    private static String firstStreetNumber(String text) {
        if (text == null) return "";
        Matcher matcher = GENERIC_NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return "";
    }

    private static String detectUnitFromKeywords(String text, String streetNumber) {
        if (text == null) return "";
        Matcher matcher = UNIT_KEYWORD_GLOBAL.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate == null || candidate.isEmpty()) continue;
            if (candidate.equalsIgnoreCase(streetNumber)) continue;
            return candidate;
        }

        Matcher trailing = TRAILING_UNIT_PATTERN.matcher(text);
        if (trailing.find()) {
            String candidate = trailing.group(1);
            if (!candidate.equalsIgnoreCase(streetNumber)) {
                return candidate;
            }
        }

        Matcher hashTail = Pattern.compile("(?i)(\\d+[A-Za-z]?)\\s*$").matcher(text);
        if (hashTail.find()) {
            String candidate = hashTail.group(1);
            if (!candidate.equalsIgnoreCase(streetNumber)) {
                return candidate;
            }
        }
        return "";
    }

    private static String heuristicUnitFromNumbers(String text, String streetNumber) {
        if (text == null) return "";
        List<String> numbers = new ArrayList<>();
        Matcher matcher = GENERIC_NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            numbers.add(matcher.group(1));
            if (numbers.size() >= 3) break;
        }
        if (numbers.size() < 2) {
            return "";
        }
        String first = numbers.get(0);
        String second = numbers.get(1);
        int firstVal = numericHint(first);
        int secondVal = numericHint(second);

        if (streetNumber != null && !streetNumber.isEmpty()) {
            if (streetNumber.equals(second) && firstVal > 0 && firstVal < secondVal && secondVal >= 1000) {
                return first;
            }
            if (streetNumber.equals(first) && secondVal > 0 && secondVal < firstVal) {
                return second;
            }
        }

        if (streetNumber == null || streetNumber.isEmpty()) {
            if (secondVal >= 1000 && firstVal > 0 && firstVal < secondVal) {
                return first;
            }
        }

        return "";
    }

    private static int numericHint(String token) {
        if (token == null) return -1;
        Matcher matcher = NUMBER_PATTERN.matcher(token);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static class UnitExtractionResult {
        final String unit;
        final String remaining;
        UnitExtractionResult(String unit, String remaining) {
            this.unit = unit == null ? "" : unit;
            this.remaining = remaining == null ? "" : remaining;
        }
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

    public static void playRing(Context ctx) {
        try {
            Uri ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            MediaPlayer mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDataSource(ctx, ringUri);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            mMediaPlayer.setLooping(false);
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        } catch (Exception e) {
            Log.e(ResourceMgr.TAG, "playRing: " + e.getMessage());
        }
    }

    public static boolean isNumeric(String str){
        Pattern pattern = Pattern.compile("[0-9]*");
        Matcher isNum = pattern.matcher(str);
        return isNum.matches();
    }

    public static String getCurrentDate() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd" , Locale.US);
        return df.format(new Date());
    }

    public static void showOnUi(Context context, String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        );
    }

}
