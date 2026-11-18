package com.tollroutefinder.search;

import com.tollroutefinder.config.CacheConfig;
import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.geo.RouteLine;
import com.tollroutefinder.pincode.PincodeLocation;
import com.tollroutefinder.pincode.PincodeLocator;
import com.tollroutefinder.routing.Route;
import com.tollroutefinder.routing.RouteNotFoundException;
import com.tollroutefinder.routing.RouteProvider;
import com.tollroutefinder.routing.RoutingUnavailableException;
import com.tollroutefinder.toll.TollPlaza;
import com.tollroutefinder.toll.TollPlazaIndex;
import com.tollroutefinder.toll.TollPlazaMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TollPlazaSearchServiceTest {

    private static final GeoPoint SOURCE = new GeoPoint(10.0, 77.0);
    private static final GeoPoint DESTINATION = new GeoPoint(12.0, 77.0);

    private final PincodeLocator pincodes = mock(PincodeLocator.class);
    private final RouteProvider routes = mock(RouteProvider.class);
    private final TollPlazaIndex index = new TollPlazaIndex();
    private TollPlazaSearchService service;

    @BeforeEach
    void setUp() {
        when(pincodes.locate("600001")).thenReturn(Optional.of(new PincodeLocation("600001", SOURCE, "D1", "S1")));
        when(pincodes.locate("600002")).thenReturn(Optional.of(new PincodeLocation("600002", DESTINATION, "D2", "S2")));
        when(pincodes.locate("999999")).thenReturn(Optional.empty());
        when(routes.route(SOURCE, DESTINATION)).thenReturn(straightRoute());
        index.replaceAll(List.of(
                new TollPlaza("b", "Second Toll Plaza", new GeoPoint(11.5, 77.0), "S"),
                new TollPlaza("a", "First Toll Plaza", new GeoPoint(10.5, 77.001), "S"),
                new TollPlaza("x", "Elsewhere Toll Plaza", new GeoPoint(20.0, 80.0), "S")));
        service = new TollPlazaSearchService(pincodes, routes, new TollPlazaMatcher(index, 1.0),
                new ConcurrentMapCacheManager(CacheConfig.TOLL_SEARCH_CACHE));
    }

    private static Route straightRoute() {
        List<GeoPoint> points = new ArrayList<>();
        for (double lat = 10.0; lat <= 12.0 + 1e-9; lat += 0.01) {
            points.add(new GeoPoint(lat, 77.0));
        }
        return new Route(new RouteLine(points), 222.4, 180, "OSRM");
    }

    @Test
    void findsTollsOnTheRouteInOrder() {
        TollPlazaSearchResult result = service.search("600001", "600002").result();

        assertThat(result.sourcePincode()).isEqualTo("600001");
        assertThat(result.destinationPincode()).isEqualTo("600002");
        assertThat(result.routeDistanceKm()).isEqualTo(222.4);
        assertThat(result.routeProvider()).isEqualTo("OSRM");
        assertThat(result.tolls()).extracting(t -> t.plaza().name())
                .containsExactly("First Toll Plaza", "Second Toll Plaza");
    }

    @Test
    void secondIdenticalSearchIsServedFromCacheWithoutRouting() {
        SearchOutcome first = service.search("600001", "600002");
        SearchOutcome second = service.search("600001", "600002");

        assertThat(first.cacheHit()).isFalse();
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.result()).isEqualTo(first.result());
        verify(routes, times(1)).route(any(), any());
    }

    @Test
    void reverseDirectionIsADifferentSearch() {
        when(routes.route(DESTINATION, SOURCE)).thenReturn(straightRoute());

        service.search("600001", "600002");
        SearchOutcome reverse = service.search("600002", "600001");

        assertThat(reverse.cacheHit()).isFalse();
    }

    @Test
    void rejectsIdenticalPincodesWithoutAnyLookup() {
        assertThatThrownBy(() -> service.search("600001", "600001"))
                .isInstanceOf(SamePincodeException.class)
                .hasMessage("Source and destination pincodes cannot be the same");
        verify(pincodes, never()).locate(any());
    }

    @Test
    void unknownSourcePincodeIsInvalid() {
        assertThatThrownBy(() -> service.search("999999", "600002"))
                .isInstanceOf(InvalidPincodeException.class)
                .hasMessageContaining("999999");
        verify(routes, never()).route(any(), any());
    }

    @Test
    void unknownDestinationPincodeIsInvalid() {
        assertThatThrownBy(() -> service.search("600001", "999999"))
                .isInstanceOf(InvalidPincodeException.class)
                .extracting(e -> ((InvalidPincodeException) e).getPincode()).isEqualTo("999999");
    }

    @Test
    void routeWithNoTollsReturnsEmptyList() {
        index.replaceAll(List.of());

        assertThat(service.search("600001", "600002").result().tolls()).isEmpty();
    }

    @Test
    void routingErrorsPropagateUnwrapped() {
        when(routes.route(SOURCE, DESTINATION)).thenThrow(new RouteNotFoundException("no road"));

        assertThatThrownBy(() -> service.search("600001", "600002")).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void failuresAreNotCached() {
        when(routes.route(SOURCE, DESTINATION))
                .thenThrow(new RoutingUnavailableException("down"))
                .thenReturn(straightRoute());

        assertThatThrownBy(() -> service.search("600001", "600002")).isInstanceOf(RoutingUnavailableException.class);
        SearchOutcome retry = service.search("600001", "600002");

        assertThat(retry.cacheHit()).isFalse();
        assertThat(retry.result().tolls()).hasSize(2);
    }
}
