package com.tollroutefinder.routing;

import com.tollroutefinder.geo.GeoPoint;

/**
 * Strategy for obtaining a driving route between two points. Implementations adapt a specific routing engine
 * (OSRM, OpenRouteService, Google, ...) so the rest of the application stays engine-agnostic. Decorators
 * ({@link RetryingRouteProvider}, {@link FallbackRouteProvider}) add resilience without the engines knowing.
 */
public interface RouteProvider {

    /**
     * @throws RouteNotFoundException      if the engine reports that no road route exists
     * @throws RoutingUnavailableException if the engine cannot be reached or misbehaves
     */
    Route route(GeoPoint from, GeoPoint to);

    /** Human-readable engine name, used in logs and response headers. */
    String name();
}
