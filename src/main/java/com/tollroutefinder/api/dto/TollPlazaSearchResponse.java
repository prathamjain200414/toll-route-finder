package com.tollroutefinder.api.dto;

import java.util.List;

/**
 * Response body; matches the shape in the assignment specification. An empty {@code tollPlazas} list means the
 * route has no toll plazas.
 */
public record TollPlazaSearchResponse(RouteSummary route, List<TollPlazaDto> tollPlazas) {
}
