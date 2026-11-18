package com.tollroutefinder.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Type-safe binding of the {@code toll-finder.*} configuration tree.
 */
@Validated
@ConfigurationProperties(prefix = "toll-finder")
public record TollFinderProperties(@Valid @NotNull Data data,
                                   @Valid @NotNull Matching matching,
                                   @Valid @NotNull Routing routing) {

    public record Data(@NotNull Resource tollPlazaCsv, @NotNull Resource pincodeCsv) {
    }

    public record Matching(@Positive double maxDistanceFromRouteKm, @Positive double duplicateRadiusKm) {
    }

    public record Routing(@Valid @NotNull Osrm osrm, @Valid @NotNull Ors ors, @Valid @NotNull Retry retry) {
    }

    public record Osrm(@NotBlank String baseUrl,
                       @NotBlank String profile,
                       @NotNull Duration connectTimeout,
                       @NotNull Duration readTimeout) {
    }

    /**
     * OpenRouteService. Enabled only when an API key is configured; otherwise OSRM is used alone.
     */
    public record Ors(@NotBlank String baseUrl,
                      @NotBlank String profile,
                      String apiKey,
                      @NotNull Duration connectTimeout,
                      @NotNull Duration readTimeout) {

        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Retry(@Min(1) int maxAttempts, @NotNull Duration backoff) {
    }
}
