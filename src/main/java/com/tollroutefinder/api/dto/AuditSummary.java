package com.tollroutefinder.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuditSummary(
        @Schema(description = "CLEAN when every charge is a first charge on the route and every route plaza was "
                + "charged; otherwise REVIEW", allowableValues = {"CLEAN", "REVIEW"}, example = "REVIEW")
        String verdict,
        @Schema(description = "Toll plazas on the planned route", example = "12") int expectedTollPlazas,
        @Schema(description = "Charges submitted", example = "15") int charged,
        @Schema(example = "11") long onRoute,
        @Schema(example = "1") long duplicateCharges,
        @Schema(example = "2") long offRoute,
        @Schema(example = "0") long unknownPlazas,
        @Schema(description = "Route plazas with no charge", example = "1") int notCharged) {
}
