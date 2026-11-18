package com.tollroutefinder.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TripSummary(
        @Schema(example = "560064") String sourcePincode,
        @Schema(example = "411045") String destinationPincode,
        @Schema(description = "Road distance in km", example = "854.0") double distanceInKm,
        @Schema(description = "Driving time estimated by the routing engine, without rest stops", example = "13.9")
        double estimatedDriveHours,
        @Schema(description = "E-way bill validity for regular cargo under CGST Rule 138(10): "
                + "1 day per 200 km or part thereof", example = "5")
        int ewayBillValidityDays,
        @Schema(example = "12") int tollPlazaCount,
        @Schema(description = "Routing engine that produced the route", example = "OpenRouteService (driving-hgv)")
        String routeProvider) {
}
