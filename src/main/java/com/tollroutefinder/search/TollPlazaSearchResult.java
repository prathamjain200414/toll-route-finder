package com.tollroutefinder.search;

import com.tollroutefinder.toll.TollOnRoute;

import java.util.List;

/**
 * Outcome of a toll plaza search. Deliberately excludes the route geometry so cached results stay small.
 *
 * @param routeDistanceKm road distance between the pincodes
 * @param routeProvider   routing engine that produced the route
 * @param tolls           plazas on the route, ordered by distance from the source
 */
public record TollPlazaSearchResult(String sourcePincode,
                                    String destinationPincode,
                                    double routeDistanceKm,
                                    String routeProvider,
                                    List<TollOnRoute> tolls) {

    public TollPlazaSearchResult {
        tolls = List.copyOf(tolls);
    }
}
