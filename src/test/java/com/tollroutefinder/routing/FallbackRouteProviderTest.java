package com.tollroutefinder.routing;

import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.geo.RouteLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallbackRouteProviderTest {

    private static final GeoPoint A = new GeoPoint(12, 77);
    private static final GeoPoint B = new GeoPoint(13, 77);

    private final RouteProvider primary = mock(RouteProvider.class);
    private final RouteProvider secondary = mock(RouteProvider.class);
    private final FallbackRouteProvider fallback = new FallbackRouteProvider(List.of(primary, secondary));

    private static Route route(String provider) {
        return new Route(new RouteLine(List.of(A, B)), 111, 90, provider);
    }

    @Test
    void usesPrimaryWhenItWorks() {
        when(primary.route(A, B)).thenReturn(route("ORS"));

        assertThat(fallback.route(A, B).provider()).isEqualTo("ORS");
        verify(secondary, never()).route(A, B);
    }

    @Test
    void fallsBackWhenPrimaryIsUnavailable() {
        when(primary.route(A, B)).thenThrow(new RoutingUnavailableException("quota exceeded"));
        when(secondary.route(A, B)).thenReturn(route("OSRM"));

        assertThat(fallback.route(A, B).provider()).isEqualTo("OSRM");
    }

    @Test
    void doesNotFallBackWhenPrimarySaysThereIsNoRoute() {
        when(primary.route(A, B)).thenThrow(new RouteNotFoundException("no road"));

        assertThatThrownBy(() -> fallback.route(A, B)).isInstanceOf(RouteNotFoundException.class);
        verify(secondary, never()).route(A, B);
    }

    @Test
    void reportsUnavailableWhenAllProvidersFail() {
        when(primary.route(A, B)).thenThrow(new RoutingUnavailableException("p down"));
        when(secondary.route(A, B)).thenThrow(new RoutingUnavailableException("s down"));

        assertThatThrownBy(() -> fallback.route(A, B))
                .isInstanceOf(RoutingUnavailableException.class)
                .hasMessageContaining("s down");
    }

    @Test
    void requiresAtLeastOneProvider() {
        assertThatThrownBy(() -> new FallbackRouteProvider(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
