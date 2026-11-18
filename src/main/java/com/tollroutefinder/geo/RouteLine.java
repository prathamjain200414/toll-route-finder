package com.tollroutefinder.geo;

import java.util.List;

/**
 * A driving route represented as a polyline, with pre-computed cumulative distances so that any nearby point can
 * be projected onto the route to get both its offset from the road and its chainage (distance from the start).
 */
public final class RouteLine {

    private final List<GeoPoint> points;
    private final double[] cumulativeKm;
    private final BoundingBox boundingBox;

    public RouteLine(List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two points");
        }
        this.points = List.copyOf(points);
        this.cumulativeKm = new double[this.points.size()];
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (int i = 0; i < this.points.size(); i++) {
            GeoPoint p = this.points.get(i);
            if (i > 0) {
                cumulativeKm[i] = cumulativeKm[i - 1] + GeoMath.haversineKm(this.points.get(i - 1), p);
            }
            minLat = Math.min(minLat, p.latitude());
            maxLat = Math.max(maxLat, p.latitude());
            minLon = Math.min(minLon, p.longitude());
            maxLon = Math.max(maxLon, p.longitude());
        }
        this.boundingBox = new BoundingBox(minLat, minLon, maxLat, maxLon);
    }

    public List<GeoPoint> points() {
        return points;
    }

    public double lengthKm() {
        return cumulativeKm[cumulativeKm.length - 1];
    }

    public BoundingBox boundingBox() {
        return boundingBox;
    }

    /**
     * Finds the closest point on the route to {@code target}.
     * <p>
     * Each segment is a few hundred metres at most, so a local equirectangular projection around the segment start
     * is accurate to well under a metre while being far cheaper than spherical cross-track maths.
     */
    public Projection project(GeoPoint target) {
        double bestOffset = Double.MAX_VALUE;
        double bestAlong = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            GeoPoint a = points.get(i);
            GeoPoint b = points.get(i + 1);
            double kx = GeoMath.KM_PER_DEGREE * Math.cos(Math.toRadians(a.latitude()));
            double ky = GeoMath.KM_PER_DEGREE;
            double bx = (b.longitude() - a.longitude()) * kx;
            double by = (b.latitude() - a.latitude()) * ky;
            double px = (target.longitude() - a.longitude()) * kx;
            double py = (target.latitude() - a.latitude()) * ky;
            double lengthSquared = bx * bx + by * by;
            double t = lengthSquared == 0 ? 0 : clamp((px * bx + py * by) / lengthSquared);
            double offset = Math.hypot(px - t * bx, py - t * by);
            if (offset < bestOffset) {
                bestOffset = offset;
                bestAlong = cumulativeKm[i] + t * (cumulativeKm[i + 1] - cumulativeKm[i]);
            }
        }
        return new Projection(bestOffset, bestAlong);
    }

    private static double clamp(double t) {
        return Math.max(0, Math.min(1, t));
    }

    /**
     * @param distanceFromLineKm shortest distance from the point to the route
     * @param distanceAlongKm    distance travelled from the route start to the closest point
     */
    public record Projection(double distanceFromLineKm, double distanceAlongKm) {
    }

    public record BoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
    }
}
