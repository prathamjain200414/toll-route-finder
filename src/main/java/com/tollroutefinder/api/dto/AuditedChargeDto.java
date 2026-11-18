package com.tollroutefinder.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditedChargeDto(
        @Schema(description = "Name as submitted", example = "Kulumepalya") String chargedName,
        @Schema(allowableValues = {"ON_ROUTE", "DUPLICATE_CHARGE", "OFF_ROUTE", "UNKNOWN_PLAZA"}, example = "ON_ROUTE")
        String status,
        @Schema(description = "Plaza the name was matched to", example = "Kulumapalya Toll Plaza") String matchedPlaza,
        @Schema(example = "13.2366") Double latitude,
        @Schema(example = "77.3108") Double longitude,
        @Schema(description = "Route chainage of the plaza (plazas on the route only)", example = "31.8")
        Double distanceFromSource,
        @Schema(description = "Distance of the plaza from the planned route (OFF_ROUTE only)", example = "38.4")
        Double distanceFromRouteKm,
        @Schema(example = "Matches a toll plaza on the planned route") String note) {
}
