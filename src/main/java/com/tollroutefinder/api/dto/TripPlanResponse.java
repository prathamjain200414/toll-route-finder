package com.tollroutefinder.api.dto;

import java.util.List;

/**
 * Trip plan: the toll plaza search enriched with what a dispatcher needs (drive time, per-plaza ETA, e-way bill
 * validity) and the route geometry for drawing it on a map.
 */
public record TripPlanResponse(TripSummary route, List<TripTollPlazaDto> tollPlazas, GeoJsonLineString path) {
}
