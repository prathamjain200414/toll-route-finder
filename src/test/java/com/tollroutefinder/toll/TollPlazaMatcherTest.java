package com.tollroutefinder.toll;

import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.geo.RouteLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TollPlazaMatcherTest {

    private final TollPlazaIndex index = new TollPlazaIndex();
    private final TollPlazaMatcher matcher = new TollPlazaMatcher(index, 1.0);

    /** A densely sampled north-bound road along longitude 77 from lat 10 to lat 12, like a real OSRM polyline. */
    private final RouteLine route = northBoundRoad(10.0, 12.0, 77.0);

    private static RouteLine northBoundRoad(double fromLat, double toLat, double lon) {
        List<GeoPoint> points = new ArrayList<>();
        for (double lat = fromLat; lat <= toLat + 1e-9; lat += 0.005) {
            points.add(new GeoPoint(lat, lon));
        }
        return new RouteLine(points);
    }

    private static TollPlaza plaza(String name, double lat, double lon) {
        return new TollPlaza(name.toLowerCase(), name, new GeoPoint(lat, lon), "Karnataka");
    }

    @BeforeEach
    void loadPlazas() {
        index.replaceAll(List.of(
                plaza("Far North", 11.5, 77.0),
                plaza("Near South", 10.2, 77.0),
                plaza("Just Beside Road", 11.0, 77.005),   // ~0.55 km east
                plaza("Too Far From Road", 11.0, 77.05),   // ~5.5 km east
                plaza("Beyond Destination", 12.5, 77.0),
                plaza("Other State", 20.0, 80.0)));
    }

    @Test
    void returnsOnlyPlazasWithinTheBufferSortedByDistanceFromSource() {
        List<TollOnRoute> result = matcher.findOnRoute(route);

        assertThat(result).extracting(t -> t.plaza().name())
                .containsExactly("Near South", "Just Beside Road", "Far North");
    }

    @Test
    void reportsDistanceAlongTheRouteNotStraightLine() {
        List<TollOnRoute> result = matcher.findOnRoute(route);

        assertThat(result.get(0).distanceFromSourceKm()).isCloseTo(22.2, within(0.1));
        assertThat(result.get(1).distanceFromSourceKm()).isCloseTo(111.2, within(0.1));
        assertThat(result.get(1).distanceFromRouteKm()).isCloseTo(0.55, within(0.02));
    }

    @Test
    void returnsEmptyListWhenNoPlazaIsNearTheRoute() {
        RouteLine elsewhere = northBoundRoad(25.0, 26.0, 85.0);

        assertThat(matcher.findOnRoute(elsewhere)).isEmpty();
    }

    @Test
    void returnsEmptyListWhenIndexIsEmpty() {
        index.replaceAll(List.of());

        assertThat(matcher.findOnRoute(route)).isEmpty();
    }

    @Test
    void indexCandidatesNeverMissAPlazaThatIsWithinTheBuffer() {
        // Candidate search is an over-approximation; exact filtering happens afterwards.
        List<TollPlaza> candidates = index.candidatesNear(route, 1.0);

        assertThat(candidates).extracting(TollPlaza::name)
                .contains("Far North", "Near South", "Just Beside Road")
                .doesNotContain("Other State");
    }

    @Test
    void reportsIndexSize() {
        assertThat(index.size()).isEqualTo(6);
    }
}
