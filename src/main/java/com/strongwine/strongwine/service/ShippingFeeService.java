package com.strongwine.strongwine.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ShippingFeeService {

    private static final double EARTH_RADIUS_KM = 6371.0d;
    private static final BigDecimal FREE_SHIPPING_SUBTOTAL_THRESHOLD = BigDecimal.valueOf(3_000_000L);
    private static final BigDecimal FEE_UNDER_5_KM = BigDecimal.valueOf(25_000L);
    private static final BigDecimal FEE_UNDER_15_KM = BigDecimal.valueOf(45_000L);
    private static final BigDecimal FEE_OVER_15_KM = BigDecimal.valueOf(75_000L);
    private static final BigDecimal FEE_UNKNOWN_DISTANCE = BigDecimal.valueOf(35_000L);

    public double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
            + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLng / 2.0d) * Math.sin(dLng / 2.0d);
        double c = 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
        return EARTH_RADIUS_KM * c;
    }

    public Double calculateDistanceKm(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (!isValidCoordinate(lat1, lng1) || !isValidCoordinate(lat2, lng2)) {
            return null;
        }
        return calculateDistanceKm(lat1.doubleValue(), lng1.doubleValue(), lat2.doubleValue(), lng2.doubleValue());
    }

    public BigDecimal calculateFee(Double distanceKm, BigDecimal subtotal) {
        if (subtotal != null && subtotal.compareTo(FREE_SHIPPING_SUBTOTAL_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }
        if (distanceKm == null || !Double.isFinite(distanceKm) || distanceKm < 0.0d) {
            return FEE_UNKNOWN_DISTANCE;
        }
        if (distanceKm <= 5.0d) {
            return FEE_UNDER_5_KM;
        }
        if (distanceKm <= 15.0d) {
            return FEE_UNDER_15_KM;
        }
        return FEE_OVER_15_KM;
    }

    public BigDecimal calculateFeeForCoordinates(
        Double destLat,
        Double destLng,
        Double warehouseLat,
        Double warehouseLng,
        BigDecimal subtotal
    ) {
        Double distanceKm = calculateDistanceKm(warehouseLat, warehouseLng, destLat, destLng);
        return calculateFee(distanceKm, subtotal);
    }

    private boolean isValidCoordinate(Double lat, Double lng) {
        return lat != null && lng != null && Double.isFinite(lat) && Double.isFinite(lng);
    }
}
