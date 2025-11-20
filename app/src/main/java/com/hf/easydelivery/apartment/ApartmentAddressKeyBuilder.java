package com.hf.easydelivery.apartment;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.easydelivery.common.Utils;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ApartmentAddressKeyBuilder {

    private static final Pattern UNIT_SUFFIX_PATTERN = Pattern.compile("(?i)(#|apt|apartment|unit|suite|ste|rm|room|fl|floor|lvl|level|locker|buzz|buzzer).*");

    private ApartmentAddressKeyBuilder() {}

    public static class KeyData {
        public final String key;
        public final String displayAddress;
        public final boolean isApartment;
        public final boolean hasStructuredKey;

        public KeyData(String key, String displayAddress, boolean isApartment, boolean hasStructuredKey) {
            this.key = key;
            this.displayAddress = displayAddress;
            this.isApartment = isApartment;
            this.hasStructuredKey = hasStructuredKey;
        }
    }

    public static KeyData fromDelivery(@Nullable DeliveryInfo info) {
        if (info == null) {
            return new KeyData("", "", false, false);
        }
        String address = info.getAddress();
        Utils.AddressInfo parsed = Utils.extractApartmentAndStreetNumber(address);
        String unit = safe(parsed.getApartmentNumber());
        String streetNumber = safe(parsed.getStreetNumber());
        String streetName = extractStreetName(address);
        String city = extractCity(address);

        boolean isApartment = !TextUtils.isEmpty(unit) && !TextUtils.isEmpty(streetNumber);
        boolean hasStructuredKey = !TextUtils.isEmpty(streetNumber) && !TextUtils.isEmpty(streetName);
        String display = buildDisplayAddress(streetNumber, streetName, city, address);
        if (!hasStructuredKey) {
            return new KeyData("", display, isApartment, false);
        }
        String key = normalizeKey(city, streetName, streetNumber);
        return new KeyData(key, display, isApartment, true);
    }

    public static String normalizeManualInput(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        int comma = trimmed.indexOf(',');
        if (comma > 0) {
            trimmed = trimmed.substring(0, comma);
        }
        trimmed = UNIT_SUFFIX_PATTERN.matcher(trimmed).replaceAll("").trim();
        return trimmed;
    }

    public static String manualKeyFromInput(@Nullable String raw) {
        String simplified = normalizeManualInput(raw);
        if (simplified.isEmpty()) return "";
        return simplified.toLowerCase(Locale.US);
    }

    public static String buildDisplayAddress(@Nullable String streetNumber,
                                             @Nullable String streetName,
                                             @Nullable String city,
                                             @Nullable String fallback) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(streetNumber)) {
            sb.append(streetNumber.trim());
        }
        if (!TextUtils.isEmpty(streetName)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(streetName.trim());
        }
        if (!TextUtils.isEmpty(city)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city.trim());
        }
        if (sb.length() == 0 && !TextUtils.isEmpty(fallback)) {
            sb.append(fallback.trim());
        }
        return sb.toString();
    }

    private static String normalizeKey(String city, String streetName, String streetNumber) {
        String c = TextUtils.isEmpty(city) ? "" : city.trim().toLowerCase(Locale.US);
        String s = TextUtils.isEmpty(streetName) ? "" : streetName.trim().toLowerCase(Locale.US);
        String n = TextUtils.isEmpty(streetNumber) ? "" : streetNumber.trim().toLowerCase(Locale.US);
        return (c + "|" + s + "|" + n).replaceAll("\\s+", " ").trim();
    }

    private static String extractStreetName(@Nullable String address) {
        if (TextUtils.isEmpty(address)) return "";
        String normalized = address.replace(',', ' ').trim();
        String[] parts = normalized.split("\\s+");
        if (parts.length <= 1) {
            return normalized;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            String token = parts[i];
            if (UNIT_SUFFIX_PATTERN.matcher(token).matches()) break;
            if (token.matches("(?i)(ns|nb|qc|bc|ab|sk|mb|on)")) break;
            if (token.matches("\\d+")) break;
            if (sb.length() > 0) sb.append(' ');
            sb.append(token);
            if (token.equalsIgnoreCase("St") || token.equalsIgnoreCase("Street")
                    || token.equalsIgnoreCase("Rd") || token.equalsIgnoreCase("Road")
                    || token.equalsIgnoreCase("Ave") || token.equalsIgnoreCase("Avenue")) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private static String extractCity(@Nullable String address) {
        if (TextUtils.isEmpty(address)) return "";
        String[] parts = address.split(",");
        if (parts.length < 2) return "";
        return parts[parts.length - 2].trim();
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
