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
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
        private final String unitSource;
        private final boolean confidentUnit;

        public AddressInfo(String apartmentNumber, String streetNumber, String unitSource, boolean confidentUnit) {
            this.apartmentNumber = apartmentNumber;
            this.streetNumber = streetNumber;
            this.unitSource = unitSource == null ? "" : unitSource;
            this.confidentUnit = confidentUnit;
        }

        public String getApartmentNumber() {
            return apartmentNumber;
        }

        public String getStreetNumber() {
            return streetNumber;
        }

        public String getUnitSource() {
            return unitSource;
        }

        public boolean hasConfidentUnit() {
            return confidentUnit;
        }
    }


    private static final Pattern APARTMENT_PATTERN = Pattern.compile("^(\\d+)-(\\d+)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("\\b\\d{1,3}\\s?\\w{1,2}\\s?\\d{1,3}\\b");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b[a-zA-Z]+\\b");

    private static final Pattern LEADING_UNIT_HYPHEN = Pattern.compile("^\\s*([A-Za-z]*\\d[A-Za-z0-9]{0,5})\\s*-\\s*(\\d{1,5})\\b");
    private static final Pattern UNIT_PREFIX_PATTERN = Pattern.compile(
            "^\\s*(?:apt|apartment|unit|suite|ste|rm|room|ph|buzzer|fl|floor|lvl|level|entrance|door|code|bldg|building|#)\\s*[:#-]?\\s*(\\w{1,8})\\s+(\\d{1,5})\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DOUBLE_NUMBER_PREFIX = Pattern.compile("^\\s*(\\d{1,4})\\s+(\\d{1,5})\\b");
    private static final Pattern HASH_ONLY_PREFIX = Pattern.compile("^\\s*#\\s*(\\w{1,8})\\b");
    private static final Pattern GENERIC_NUMBER_PATTERN = Pattern.compile("\\b(\\d{1,5}[A-Za-z]?)\\b");
    private static final Pattern POSTAL_CODE_CA = Pattern.compile("(?i)\\b[A-Z]\\d[A-Z]\\s?\\d[A-Z]\\d\\b");
    private static final Pattern UNIT_KEYWORD_GLOBAL = Pattern.compile(
            "(?i)(?:\\b(?:apt|apartment|unit|suite|ste|rm|room|ph|buzzer|fl|floor|lvl|level|entrance|door|code|bldg|building|locker|buzz)\\s*[:#-]?\\s*(\\w{1,8}))"
    );
    private static final Pattern TRAILING_UNIT_PATTERN = Pattern.compile("(?i)(?:#|no\\.?|unit)\\s*(\\w{1,8})\\s*$");
    private static final Pattern EMBEDDED_UNIT_TOKEN = Pattern.compile("(?i)^(?:apt|apartment|unit|suite|ste|rm|room|fl|floor|lvl|level|locker|buzzer|buzz)[:#-]?\\s*(\\w{1,8})$");
    private static final Pattern HASHED_UNIT_TOKEN = Pattern.compile("^#\\s*(\\w{1,8})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_HYPHEN_UNIT = Pattern.compile("^(\\d{1,4})-(\\d{1,5})$");
    private static final Set<String> UNIT_KEYWORDS = new HashSet<>(Arrays.asList(
            "apt","apartment","unit","suite","ste","rm","room","ph","buzzer","fl","floor","lvl","level","entrance","door","code","bldg","building","locker","buzz"
    ));
    private static final Set<String> PROVINCE_CODES = new HashSet<>(Arrays.asList(
            "NS","NB","PE","NL","QC","ON","MB","SK","AB","BC"
    ));
    private static final Set<String> COUNTRY_CODES = new HashSet<>(Arrays.asList(
            "CA","CANADA"
    ));
    private static final Set<String> STREET_SUFFIXES = new HashSet<>(Arrays.asList(
            "st","street","rd","road","ave","avenue","blvd","boulevard","dr","drive",
            "ln","lane","crt","court","close","pl","place","way","terr","terrace",
            "cres","crescent","cir","circle","pkwy","parkway","hwy","highway","trl","trail",
            "row","path","wharf","quay","sq","square"
    ));


    public String getTodayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());
    }

    public static AddressInfo extractApartmentAndStreetNumber(String strAddress) {
        if (strAddress == null || strAddress.trim().isEmpty()) {
            return new AddressInfo("", "", "", false);
        }

        String normalized = normalizeAddress(strAddress);
        String relevant = relevantAddressLine(normalized);
        UnitExtractionResult leading = extractLeadingUnit(relevant);
        String working = leading.remaining;

        String streetNumber = firstStreetNumber(working);
        if (streetNumber.isEmpty()) {
            streetNumber = firstStreetNumber(relevant);
        }
        if (streetNumber.isEmpty()) {
            streetNumber = firstStreetNumber(normalized);
        }

        String apartment = leading.unit;
        String unitSource = leading.source;
        if (apartment.isEmpty()) {
            UnitCandidate candidate = detectUnitFromKeywords(relevant, streetNumber);
            apartment = candidate.unit;
            unitSource = candidate.source;
        }
        if (apartment.isEmpty()) {
            UnitCandidate candidate = detectTrailingUnitAfterStreetSuffix(relevant, streetNumber);
            apartment = candidate.unit;
            unitSource = candidate.source;
        }
        if (apartment.isEmpty()) {
            UnitCandidate candidate = fallbackUnitFromTokens(relevant, streetNumber);
            apartment = candidate.unit;
            unitSource = candidate.source;
        }

        return new AddressInfo(apartment, streetNumber, unitSource, isConfidentUnitSource(unitSource));
    }

    private static String normalizeAddress(String raw) {
        String stripped = stripNonAddressPrefix(raw == null ? "" : raw);
        stripped = stripped.replaceAll("\\s*,\\s*", ", ");
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
            return new UnitExtractionResult(hyphen.group(1), working.substring(hyphen.start(2)).trim(), "leading_hyphen");
        }

        Matcher prefix = UNIT_PREFIX_PATTERN.matcher(working);
        if (prefix.find()) {
            return new UnitExtractionResult(prefix.group(1), working.substring(prefix.start(2)).trim(), "unit_prefix");
        }

        Matcher doubleNumbers = DOUBLE_NUMBER_PREFIX.matcher(working);
        if (doubleNumbers.find()) {
            return new UnitExtractionResult(doubleNumbers.group(1), working.substring(doubleNumbers.start(2)).trim(), "double_number_prefix");
        }

        Matcher hashOnly = HASH_ONLY_PREFIX.matcher(working);
        if (hashOnly.find()) {
            return new UnitExtractionResult(hashOnly.group(1), working.substring(hashOnly.end()).trim(), "hash_prefix");
        }

        return new UnitExtractionResult("", working, "");
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

    private static UnitCandidate detectUnitFromKeywords(String text, String streetNumber) {
        if (text == null) return UnitCandidate.empty();
        Matcher matcher = UNIT_KEYWORD_GLOBAL.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate == null || candidate.isEmpty()) continue;
            if (candidate.equalsIgnoreCase(streetNumber)) continue;
            return new UnitCandidate(candidate, "keyword_global");
        }

        Matcher trailing = TRAILING_UNIT_PATTERN.matcher(text);
        if (trailing.find()) {
            String candidate = trailing.group(1);
            if (!candidate.equalsIgnoreCase(streetNumber)) {
                return new UnitCandidate(candidate, "trailing_keyword");
            }
        }
        return UnitCandidate.empty();
    }

    private static UnitCandidate detectTrailingUnitAfterStreetSuffix(String text, String streetNumber) {
        if (text == null || text.isEmpty() || streetNumber == null || streetNumber.isEmpty()) {
            return UnitCandidate.empty();
        }
        String[] tokens = text.split("\\s+");
        boolean seenStreetNumber = false;
        boolean seenStreetSuffix = false;
        for (int i = 0; i < tokens.length; i++) {
            String sanitized = sanitizeToken(tokens[i]);
            if (sanitized.isEmpty()) continue;
            if (!seenStreetNumber) {
                if (sanitized.equalsIgnoreCase(streetNumber)) {
                    seenStreetNumber = true;
                }
                continue;
            }
            if (!seenStreetSuffix) {
                if (STREET_SUFFIXES.contains(sanitized.toLowerCase(Locale.US))) {
                    seenStreetSuffix = true;
                }
                continue;
            }

            if (UNIT_KEYWORDS.contains(sanitized.toLowerCase(Locale.US)) && i + 1 < tokens.length) {
                String next = sanitizeToken(tokens[i + 1]);
                if (looksLikeUnitToken(next, streetNumber)) {
                    return new UnitCandidate(next, "street_suffix_keyword");
                }
                return UnitCandidate.empty();
            }

            if (looksLikeUnitToken(sanitized, streetNumber)) {
                return new UnitCandidate(sanitized, "street_suffix_trailing");
            }

            if (isLocationTailToken(tokens[i])) {
                return UnitCandidate.empty();
            }
        }
        return UnitCandidate.empty();
    }

    private static UnitCandidate fallbackUnitFromTokens(String text, String streetNumber) {
        if (text == null || text.isEmpty()) return UnitCandidate.empty();
        String[] tokens = text.split("\\s+");

        for (String token : tokens) {
            Matcher hyphen = INLINE_HYPHEN_UNIT.matcher(token);
            if (hyphen.find()) {
                String candidate = hyphen.group(1);
                if (!candidate.equalsIgnoreCase(streetNumber)) {
                    return new UnitCandidate(candidate, "inline_hyphen");
                }
            }
        }

        for (int i = 0; i < tokens.length; i++) {
            String raw = tokens[i];
            if (raw == null || raw.isEmpty()) continue;

            Matcher embedded = EMBEDDED_UNIT_TOKEN.matcher(raw);
            if (embedded.find()) {
                String candidate = embedded.group(1);
                if (candidate != null && !candidate.isEmpty() && !candidate.equalsIgnoreCase(streetNumber)) {
                    return new UnitCandidate(candidate, "embedded_unit_token");
                }
            }

            Matcher hash = HASHED_UNIT_TOKEN.matcher(raw);
            if (hash.find()) {
                String candidate = hash.group(1);
                if (candidate != null && !candidate.isEmpty() && !candidate.equalsIgnoreCase(streetNumber)) {
                    return new UnitCandidate(candidate, "hashed_unit_token");
                }
            }

            String keyword = raw.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.US);
            if (UNIT_KEYWORDS.contains(keyword) && i + 1 < tokens.length) {
                String next = tokens[i + 1].replaceAll("[^0-9A-Za-z]", "");
                if (looksLikeUnitToken(next, streetNumber)) {
                    return new UnitCandidate(next, "unit_keyword_token");
                }
            }
        }

        return UnitCandidate.empty();
    }

    private static String relevantAddressLine(String normalized) {
        if (normalized == null || normalized.isEmpty()) return "";
        String[] segments = normalized.split("\\s*,\\s*");
        if (segments.length == 0) return normalized;
        StringBuilder sb = new StringBuilder();
        sb.append(segments[0].trim());
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i].trim();
            if (segment.isEmpty()) continue;
            if (looksLikeUnitSegment(segment)) {
                sb.append(' ').append(segment);
                continue;
            }
            break;
        }
        String candidate = stripCanadianTail(sb.toString());
        return candidate.isEmpty() ? stripCanadianTail(normalized) : candidate;
    }

    private static boolean looksLikeUnitSegment(String segment) {
        if (segment == null || segment.isEmpty()) return false;
        String lower = segment.toLowerCase(Locale.US);
        if (lower.startsWith("#")) return true;
        for (String keyword : UNIT_KEYWORDS) {
            if (lower.startsWith(keyword + " ") || lower.equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String stripCanadianTail(String text) {
        if (text == null || text.isEmpty()) return "";
        String working = POSTAL_CODE_CA.matcher(text).replaceAll(" ");
        List<String> tokens = new ArrayList<>(Arrays.asList(working.split("\\s+")));
        while (!tokens.isEmpty()) {
            String last = sanitizeToken(tokens.get(tokens.size() - 1)).toUpperCase(Locale.US);
            if (last.isEmpty()) {
                tokens.remove(tokens.size() - 1);
                continue;
            }
            if (PROVINCE_CODES.contains(last) || COUNTRY_CODES.contains(last)) {
                tokens.remove(tokens.size() - 1);
                continue;
            }
            break;
        }
        return String.join(" ", tokens).trim();
    }

    private static String sanitizeToken(String token) {
        return token == null ? "" : token.replaceAll("[^0-9A-Za-z]", "");
    }

    private static boolean looksLikeUnitToken(String token, String streetNumber) {
        String candidate = sanitizeToken(token);
        if (candidate.isEmpty()) return false;
        if (candidate.equalsIgnoreCase(streetNumber)) return false;
        return candidate.matches(".*\\d.*");
    }

    private static boolean isLocationTailToken(String token) {
        String sanitized = sanitizeToken(token);
        if (sanitized.isEmpty()) return false;
        if (PROVINCE_CODES.contains(sanitized.toUpperCase(Locale.US))) return true;
        if (COUNTRY_CODES.contains(sanitized.toUpperCase(Locale.US))) return true;
        return sanitized.matches("[A-Za-z]{2,}");
    }

    private static boolean isConfidentUnitSource(String source) {
        return "leading_hyphen".equals(source)
                || "unit_prefix".equals(source)
                || "hash_prefix".equals(source)
                || "keyword_global".equals(source)
                || "trailing_keyword".equals(source)
                || "street_suffix_keyword".equals(source)
                || "street_suffix_trailing".equals(source)
                || "inline_hyphen".equals(source)
                || "embedded_unit_token".equals(source)
                || "hashed_unit_token".equals(source)
                || "unit_keyword_token".equals(source)
                || "double_number_prefix".equals(source);
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
        final String source;
        UnitExtractionResult(String unit, String remaining, String source) {
            this.unit = unit == null ? "" : unit;
            this.remaining = remaining == null ? "" : remaining;
            this.source = source == null ? "" : source;
        }
    }

    private static class UnitCandidate {
        final String unit;
        final String source;

        UnitCandidate(String unit, String source) {
            this.unit = unit == null ? "" : unit;
            this.source = source == null ? "" : source;
        }

        static UnitCandidate empty() {
            return new UnitCandidate("", "");
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
