package com.strongwine.strongwine.service;

import org.springframework.stereotype.Service;

@Service
public class VietnamGeoValidationService {

    private static final double MIN_LAT = 8.2;
    private static final double MAX_LAT = 23.6;
    private static final double MIN_LNG = 102.0;
    private static final double MAX_LNG = 109.7;

    // Rough polygon enclosing mainland Vietnam to block out-of-country points.
    private static final double[][] MAINLAND_VIETNAM_POLYGON = {
            {23.23, 102.14},
            {22.50, 104.70},
            {22.85, 106.70},
            {21.55, 108.20},
            {20.50, 108.10},
            {19.20, 107.70},
            {18.00, 107.50},
            {16.80, 108.20},
            {15.50, 108.10},
            {14.00, 109.10},
            {12.30, 109.30},
            {10.80, 106.80},
            {9.40, 105.80},
            {8.70, 104.90},
            {9.70, 104.00},
            {10.90, 103.30},
            {12.20, 104.60},
            {14.10, 107.40},
            {16.00, 106.80},
            {18.10, 105.80},
            {20.40, 104.10},
            {22.00, 103.10}
    };

    public boolean isWithinMainlandVietnam(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return false;
        }
        if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) {
            return false;
        }
        return isPointInsidePolygon(lat, lng, MAINLAND_VIETNAM_POLYGON);
    }

    private boolean isPointInsidePolygon(double lat, double lng, double[][] polygon) {
        boolean inside = false;
        int j = polygon.length - 1;
        for (int i = 0; i < polygon.length; i++) {
            double yi = polygon[i][0];
            double xi = polygon[i][1];
            double yj = polygon[j][0];
            double xj = polygon[j][1];

            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lng < (xj - xi) * (lat - yi) / ((yj - yi) + 1e-12) + xi);
            if (intersect) {
                inside = !inside;
            }
            j = i;
        }
        return inside;
    }
}
