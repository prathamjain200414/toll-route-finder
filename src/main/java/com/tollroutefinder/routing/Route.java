package com.tollroutefinder.routing;

import com.tollroutefinder.geo.RouteLine;

/**
 * A driving route returned by a {@link RouteProvider}.
 *
 * @param line            the route geometry
 * @param distanceKm      road distance as reported by the provider
 * @param durationMinutes estimated driving time as reported by the provider
 * @param provider        name of the routing engine that produced the route (e.g. "OSRM")
 */
public record Route(RouteLine line, double distanceKm, double durationMinutes, String provider) {
}
