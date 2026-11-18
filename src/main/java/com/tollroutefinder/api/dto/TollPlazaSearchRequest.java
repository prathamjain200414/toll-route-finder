package com.tollroutefinder.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/toll-plazas}.
 */
public record TollPlazaSearchRequest(
        @Schema(description = "6-digit Indian pincode of the start point", example = "560064")
        @NotBlank(message = "is required")
        @Pattern(regexp = PINCODE_REGEX, message = PINCODE_MESSAGE)
        String sourcePincode,

        @Schema(description = "6-digit Indian pincode of the end point", example = "411045")
        @NotBlank(message = "is required")
        @Pattern(regexp = PINCODE_REGEX, message = PINCODE_MESSAGE)
        String destinationPincode) {

    /** Geographic Indian pincodes are 6 digits starting with 1-8 (9 is reserved for the Army Postal Service). */
    public static final String PINCODE_REGEX = "^[1-8][0-9]{5}$";
    public static final String PINCODE_MESSAGE = "must be a 6-digit Indian pincode starting with 1-8";
}
