package com.strongwine.strongwine.util;

import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AddressTextUtils {

    private static final Pattern LEGACY_GPS_SUFFIX = Pattern.compile(
            "\\s*\\[GPS:\\s*([-+]?\\d{1,3}(?:\\.\\d+)?)\\s*,\\s*([-+]?\\d{1,3}(?:\\.\\d+)?)\\s*\\]\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ZERO_WIDTH_CHARS = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern UNICODE_WHITESPACE_RUN = Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private AddressTextUtils() {
    }

    /**
     * Normalizes user-entered address text using Unicode NFC, stripping zero-width characters,
     * and collapsing all Unicode separators and whitespace runs to single ASCII spaces.
     */
    public static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String withoutZeroWidth = ZERO_WIDTH_CHARS.matcher(value).replaceAll("");
        String nfc = Normalizer.normalize(withoutZeroWidth, Normalizer.Form.NFC);
        return UNICODE_WHITESPACE_RUN.matcher(nfc).replaceAll(" ").trim();
    }

    /**
     * Removes Vietnamese diacritics and maps đ/Đ cleanly to ASCII d/D.
     */
    public static String stripAccents(String value) {
        if (value == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed)
                .replaceAll("")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D');
    }

    /**
     * Validates that both latitude and longitude are finite WGS84 coordinates within [-90, 90] and [-180, 180].
     */
    public static boolean isValidCoordinatePair(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return false;
        }
        return latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    public static String stripLegacyGpsSuffix(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            return normalized;
        }

        Matcher matcher = LEGACY_GPS_SUFFIX.matcher(normalized);
        if (!matcher.find()) {
            return normalized;
        }

        String stripped = normalized.substring(0, matcher.start()).trim();
        return stripped.isEmpty() ? normalized : stripped;
    }

    public static Optional<Coordinates> extractLegacyCoordinates(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        Matcher matcher = LEGACY_GPS_SUFFIX.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }

        try {
            double latitude = Double.parseDouble(matcher.group(1));
            double longitude = Double.parseDouble(matcher.group(2));
            if (!isValidCoordinatePair(latitude, longitude)) {
                return Optional.empty();
            }
            return Optional.of(new Coordinates(latitude, longitude));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public record Coordinates(double latitude, double longitude) {
    }
}
