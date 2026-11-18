package com.tollroutefinder.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GeoMathTest {

    @Test
    void distanceToSamePointIsZero() {
        GeoPoint p = new GeoPoint(12.9716, 77.5946);
        assertThat(GeoMath.haversineKm(p, p)).isZero();
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertThat(GeoMath.haversineKm(new GeoPoint(10, 77), new GeoPoint(11, 77)))
                .isCloseTo(111.19, within(0.05));
    }

    @Test
    void delhiToBengaluruGreatCircleDistance() {
        GeoPoint delhi = new GeoPoint(28.6139, 77.2090);
        GeoPoint bengaluru = new GeoPoint(12.9716, 77.5946);
        assertThat(GeoMath.haversineKm(delhi, bengaluru)).isCloseTo(1740.0, within(5.0));
    }

    @Test
    void distanceIsSymmetric() {
        GeoPoint a = new GeoPoint(18.5204, 73.8567);
        GeoPoint b = new GeoPoint(19.0760, 72.8777);
        assertThat(GeoMath.haversineKm(a, b)).isEqualTo(GeoMath.haversineKm(b, a));
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertThatThrownBy(() -> new GeoPoint(91, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, 181)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
