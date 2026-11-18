package com.tollroutefinder.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RouteSummary(
        @Schema(example = "560064") String sourcePincode,
        @Schema(example = "411045") String destinationPincode,
        @Schema(description = "Road distance in km", example = "856.5") double distanceInKm) {
}
