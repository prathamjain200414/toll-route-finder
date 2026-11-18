package com.tollroutefinder.api.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.tollroutefinder.api.dto.TollPlazaSearchRequest.PINCODE_MESSAGE;
import static com.tollroutefinder.api.dto.TollPlazaSearchRequest.PINCODE_REGEX;

/**
 * Request body for {@code POST /api/v1/trips/toll-audit}: a one-way trip and the toll plazas it was charged for.
 */
public record TollAuditRequest(
        @Schema(description = "6-digit Indian pincode of the start point", example = "560064")
        @NotBlank(message = "is required")
        @Pattern(regexp = PINCODE_REGEX, message = PINCODE_MESSAGE)
        String sourcePincode,

        @Schema(description = "6-digit Indian pincode of the end point", example = "411045")
        @NotBlank(message = "is required")
        @Pattern(regexp = PINCODE_REGEX, message = PINCODE_MESSAGE)
        String destinationPincode,

        @ArraySchema(arraySchema = @Schema(description = "Toll plaza names as they appear on the FASTag statement "
                + "or transporter invoice, in any spelling",
                example = "[\"Nelamangala\", \"Kulumepalya\", \"Chokkanahalli\", \"Karajeevanahalli\", \"Guilalu\", "
                        + "\"Kannanakatte\", \"Thimmalapura\", \"Hitnal\", \"Hitnal\", \"Shahapur\", \"Vanagari\", "
                        + "\"Nagarhalla\", \"Kasaba Bijapur\", \"Sardewadi\", \"Khed Shivapur\"]"))
        @NotNull(message = "is required")
        @Size(max = 200, message = "must have at most 200 entries")
        List<@NotBlank(message = "must not be blank") @Size(max = 120, message = "is too long") String> chargedTollPlazas) {
}
