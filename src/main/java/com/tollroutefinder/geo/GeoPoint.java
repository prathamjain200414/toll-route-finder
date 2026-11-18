package com.tollroutefinder.geo;

/**
 * An immutable WGS-84 coordinate. Construction fails fast on impossible values so that bad data
 * never propagates into distance calculations.
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude out of range: " + latitude);
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude out of range: " + longitude);
        }
    }
}
