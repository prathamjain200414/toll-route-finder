package com.tollroutefinder.toll;

/**
 * A toll plaza found on a route.
 *
 * @param plaza                the plaza
 * @param distanceFromSourceKm driving distance from the route start to the plaza (route chainage)
 * @param distanceFromRouteKm  how far the plaza's recorded coordinate is from the route line
 */
public record TollOnRoute(TollPlaza plaza, double distanceFromSourceKm, double distanceFromRouteKm) {
}
