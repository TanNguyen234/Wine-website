package com.strongwine.strongwine.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AddressTextUtils {

    private static final Pattern LEGACY_GPS_SUFFIX = Pattern.compile("\\s*\\[GPS:\\s*([-+]?\\d{1,2}(?:\\.\\d+)?)\\s*,\\s*([-+]?\\d{1,3}(?:\\.\\d+)?)\\s*\\]\\s*$");

    private AddressTextUtils() {
    }

    public static String stripLegacyGpsSuffix(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        Matcher matcher = LEGACY_GPS_SUFFIX.matcher(trimmed);
        if (!matcher.find()) {
            return trimmed;
        }

        String stripped = trimmed.substring(0, matcher.start()).trim();
        return stripped.isEmpty() ? trimmed : stripped;
    }

    public static Optional<Coordinates> extractLegacyCoordinates(String value) {
        if (value == null) {
            return Optional.empty();
        }

        Matcher matcher = LEGACY_GPS_SUFFIX.matcher(value.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }

        try {
            double latitude = Double.parseDouble(matcher.group(1));
            double longitude = Double.parseDouble(matcher.group(2));
            return Optional.of(new Coordinates(latitude, longitude));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public record Coordinates(double latitude, double longitude) {
    }
}
