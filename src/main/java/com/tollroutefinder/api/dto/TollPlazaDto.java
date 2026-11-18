package com.tollroutefinder.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TollPlazaDto(
        @Schema(example = "Hulikunte Toll Plaza") String name,
        @Schema(example = "13.2710263") double latitude,
        @Schema(example = "77.3475844") double longitude,
        @Schema(description = "Driving distance from the source pincode along the route, in km", example = "43.3")
        double distanceFromSource) {
}
