package com.tollroutefinder.api;

import com.tollroutefinder.api.dto.RouteSummary;
import com.tollroutefinder.api.dto.TollPlazaDto;
import com.tollroutefinder.api.dto.TollPlazaSearchResponse;
import com.tollroutefinder.search.TollPlazaSearchResult;
import com.tollroutefinder.toll.TollOnRoute;
import org.springframework.stereotype.Component;

/**
 * Maps domain results to the public API shape. Distances are rounded to 0.1 km, which is more precise than the
 * underlying data (plaza coordinates are often a few hundred metres off).
 */
@Component
public class TollPlazaResponseMapper {

    public TollPlazaSearchResponse toResponse(TollPlazaSearchResult result) {
        return new TollPlazaSearchResponse(
                new RouteSummary(result.sourcePincode(), result.destinationPincode(), round(result.routeDistanceKm())),
                result.tolls().stream().map(TollPlazaResponseMapper::toDto).toList());
    }

    private static TollPlazaDto toDto(TollOnRoute toll) {
        return new TollPlazaDto(toll.plaza().name(), toll.plaza().location().latitude(),
                toll.plaza().location().longitude(), round(toll.distanceFromSourceKm()));
    }

    static double round(double km) {
        return Math.round(km * 10) / 10.0;
    }
}
