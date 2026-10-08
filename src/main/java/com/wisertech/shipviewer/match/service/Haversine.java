// metresBetween: returns the great-circle distance in metres between two latitude/longitude points.

package com.wisertech.shipviewer.match.service;

public final class Haversine {
    private static final double EARTH_RADIUS_METRES = 6_371_000.0;

    private Haversine() {
    }

    public static double metresBetween(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = phi2 - phi1;
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.pow(Math.sin(deltaPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(deltaLambda / 2), 2);
        return 2 * EARTH_RADIUS_METRES * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
