package com.tollroutefinder.routing;

import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.geo.RouteLine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryingRouteProviderTest {

    private static final GeoPoint A = new GeoPoint(12, 77);
    private static final GeoPoint B = new GeoPoint(13, 77);
    private static final Route ROUTE = new Route(new RouteLine(List.of(A, B)), 111, 90, "test");

    private final RouteProvider delegate = mock(RouteProvider.class);
    private final RetryingRouteProvider retrying = new RetryingRouteProvider(delegate, 3, Duration.ZERO);

    @Test
    void returnsImmediatelyOnSuccess() {
        when(delegate.route(A, B)).thenReturn(ROUTE);

        assertThat(retrying.route(A, B)).isSameAs(ROUTE);
        verify(delegate, times(1)).route(A, B);
    }

    @Test
    void retriesTransientFailuresUntilSuccess() {
        when(delegate.route(A, B))
                .thenThrow(new RoutingUnavailableException("timeout"))
                .thenThrow(new RoutingUnavailableException("timeout"))
                .thenReturn(ROUTE);

        assertThat(retrying.route(A, B)).isSameAs(ROUTE);
        verify(delegate, times(3)).route(A, B);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        when(delegate.route(any(), any())).thenThrow(new RoutingUnavailableException("down"));

        assertThatThrownBy(() -> retrying.route(A, B)).isInstanceOf(RoutingUnavailableException.class);
        verify(delegate, times(3)).route(A, B);
    }

    @Test
    void doesNotRetryWhenNoRouteExists() {
        when(delegate.route(any(), any())).thenThrow(new RouteNotFoundException("island"));

        assertThatThrownBy(() -> retrying.route(A, B)).isInstanceOf(RouteNotFoundException.class);
        verify(delegate, times(1)).route(A, B);
    }

    @Test
    void exposesDelegateName() {
        when(delegate.name()).thenReturn("OSRM");

        assertThat(retrying.name()).isEqualTo("OSRM");
    }
}
