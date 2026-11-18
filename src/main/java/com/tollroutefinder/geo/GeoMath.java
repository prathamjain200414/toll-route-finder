package com.tollroutefinder.geo;

/**
 * Spherical-earth distance helpers.
 */
public final class GeoMath {

    public static final double EARTH_RADIUS_KM = 6371.0088;

    /** Kilometres per degree of latitude (and of longitude at the equator). */
    public static final double KM_PER_DEGREE = Math.toRadians(1) * EARTH_RADIUS_KM;

    private GeoMath() {
    }

    /** Great-circle distance between two points using the haversine formula. */
    public static double haversineKm(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /**
     * Converts a distance in km to a conservative degree buffer, i.e. one that is at least {@code km} wide in
     * both axes anywhere in India (latitudes below ~37 degrees).
     */
    public static double kmToDegreeBuffer(double km) {
        return km / (KM_PER_DEGREE * Math.cos(Math.toRadians(37.5)));
    }
}
