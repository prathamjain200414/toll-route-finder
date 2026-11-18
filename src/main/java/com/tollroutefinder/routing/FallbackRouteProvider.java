package com.tollroutefinder.routing;

import com.tollroutefinder.geo.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Chain of providers tried in order: if one is unavailable (down, rate-limited, out of quota) the next is used.
 * A "no route exists" answer is authoritative and is returned immediately.
 */
public class FallbackRouteProvider implements RouteProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackRouteProvider.class);

    private final List<RouteProvider> providers;

    public FallbackRouteProvider(List<RouteProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one route provider is required");
        }
        this.providers = List.copyOf(providers);
    }

    @Override
    public Route route(GeoPoint from, GeoPoint to) {
        RoutingUnavailableException last = null;
        for (RouteProvider provider : providers) {
            try {
                return provider.route(from, to);
            } catch (RoutingUnavailableException e) {
                log.warn("Route provider {} unavailable: {}", provider.name(), e.getMessage());
                last = e;
            }
        }
        throw last;
    }

    @Override
    public String name() {
        return providers.stream().map(RouteProvider::name).collect(Collectors.joining(" -> "));
    }
}
