package com.tollroutefinder.search;

import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.toll.TollOnRoute;

import java.util.List;

/**
 * Outcome of a toll plaza search. Holds a simplified copy of the route geometry (enough to draw it on a map or
 * measure how far a plaza is from it) rather than the full polyline, so cached results stay small.
 *
 * @param routeDistanceKm     road distance between the pincodes
 * @param routeDurationMinutes driving time estimated by the routing engine (no rest stops)
 * @param routeProvider       routing engine that produced the route
 * @param simplifiedPath      route geometry simplified to ~100 m tolerance
 * @param tolls               plazas on the route, ordered by distance from the source
 */
public record TollPlazaSearchResult(String sourcePincode,
                                    String destinationPincode,
                                    double routeDistanceKm,
                                    double routeDurationMinutes,
                                    String routeProvider,
                                    List<GeoPoint> simplifiedPath,
                                    List<TollOnRoute> tolls) {

    public TollPlazaSearchResult {
        simplifiedPath = List.copyOf(simplifiedPath);
        tolls = List.copyOf(tolls);
    }
}
