package com.tollroutefinder.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Error body. {@code error} matches the assignment's format; {@code details} adds field-level specifics when useful.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        @Schema(example = "Invalid source or destination pincode") String error,
        @Schema(example = "[\"sourcePincode: must be a 6-digit Indian pincode starting with 1-8\"]") List<String> details) {

    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, List.of());
    }
}
