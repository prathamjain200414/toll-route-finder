package com.tollroutefinder.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RouteLineTest {

    // A straight north-bound line along longitude 77 from lat 10 to lat 12 (~222 km), with a bend east at the end.
    private final RouteLine line = new RouteLine(List.of(
            new GeoPoint(10.0, 77.0),
            new GeoPoint(11.0, 77.0),
            new GeoPoint(12.0, 77.0),
            new GeoPoint(12.0, 78.0)));

    @Test
    void totalLengthIsSumOfSegments() {
        double expected = GeoMath.haversineKm(new GeoPoint(10, 77), new GeoPoint(12, 77))
                + GeoMath.haversineKm(new GeoPoint(12, 77), new GeoPoint(12, 78));
        assertThat(line.lengthKm()).isCloseTo(expected, within(1e-6));
    }

    @Test
    void pointOnTheLineHasZeroOffsetAndCorrectChainage() {
        RouteLine.Projection p = line.project(new GeoPoint(10.5, 77.0));
        assertThat(p.distanceFromLineKm()).isCloseTo(0.0, within(1e-3));
        assertThat(p.distanceAlongKm()).isCloseTo(55.6, within(0.1));
    }

    @Test
    void pointBesideTheLineIsProjectedPerpendicularly() {
        // 0.01 deg of longitude at lat 11 is about 1.09 km
        RouteLine.Projection p = line.project(new GeoPoint(11.0, 77.01));
        assertThat(p.distanceFromLineKm()).isCloseTo(1.09, within(0.02));
        assertThat(p.distanceAlongKm()).isCloseTo(111.19, within(0.1));
    }

    @Test
    void pointBeforeTheStartSnapsToTheStart() {
        RouteLine.Projection p = line.project(new GeoPoint(9.9, 77.0));
        assertThat(p.distanceAlongKm()).isZero();
        assertThat(p.distanceFromLineKm()).isCloseTo(11.1, within(0.1));
    }

    @Test
    void pointNearTheSecondLegIsMeasuredAlongTheWholeRoute() {
        RouteLine.Projection p = line.project(new GeoPoint(12.0, 77.5));
        assertThat(p.distanceFromLineKm()).isCloseTo(0.0, within(1e-3));
        assertThat(p.distanceAlongKm()).isCloseTo(222.39 + 54.4, within(0.3));
    }

    @Test
    void boundingBoxCoversAllPoints() {
        RouteLine.BoundingBox box = line.boundingBox();
        assertThat(box.minLat()).isEqualTo(10.0);
        assertThat(box.maxLat()).isEqualTo(12.0);
        assertThat(box.minLon()).isEqualTo(77.0);
        assertThat(box.maxLon()).isEqualTo(78.0);
    }

    @Test
    void needsAtLeastTwoPoints() {
        assertThatThrownBy(() -> new RouteLine(List.of(new GeoPoint(1, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simplificationDropsCollinearPointsButKeepsTheEnds() {
        List<GeoPoint> points = new java.util.ArrayList<>();
        for (double lat = 10.0; lat <= 12.0 + 1e-9; lat += 0.01) {
            points.add(new GeoPoint(lat, 77.0));
        }
        points.add(new GeoPoint(12.0, 78.0));

        List<GeoPoint> simplified = new RouteLine(points).simplified(0.1);

        assertThat(simplified).hasSize(3);
        assertThat(simplified.get(0)).isEqualTo(points.get(0));
        assertThat(simplified.get(2)).isEqualTo(new GeoPoint(12.0, 78.0));
    }
}
