package com.tollroutefinder.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TripTollPlazaDto(
        @Schema(example = "Hitnal Toll Plaza") String name,
        @Schema(example = "15.2862") double latitude,
        @Schema(example = "76.3398") double longitude,
        @Schema(description = "Driving distance from the source pincode along the route, in km", example = "334.5")
        double distanceFromSource,
        @Schema(description = "Estimated driving hours from the start to this plaza (same pace as the whole route)",
                example = "5.4")
        double etaHoursFromStart) {
}
