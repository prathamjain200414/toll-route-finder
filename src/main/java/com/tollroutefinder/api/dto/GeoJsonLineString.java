package com.tollroutefinder.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A GeoJSON LineString ({@code [longitude, latitude]} pairs), ready for Leaflet, Mapbox or geojson.io.
 */
public record GeoJsonLineString(
        @Schema(example = "LineString") String type,
        @Schema(description = "[longitude, latitude] pairs along the route") List<double[]> coordinates) {

    public static GeoJsonLineString of(List<double[]> coordinates) {
        return new GeoJsonLineString("LineString", coordinates);
    }
}
