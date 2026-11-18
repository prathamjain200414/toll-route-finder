package com.tollroutefinder.api;

import com.tollroutefinder.api.dto.ErrorResponse;
import com.tollroutefinder.api.dto.TollPlazaSearchRequest;
import com.tollroutefinder.api.dto.TollPlazaSearchResponse;
import com.tollroutefinder.search.SearchOutcome;
import com.tollroutefinder.search.TollPlazaSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/toll-plazas")
@Tag(name = "Toll plazas", description = "Find toll plazas on the driving route between two Indian pincodes")
public class TollPlazaController {

    static final String CACHE_HEADER = "X-Cache";
    static final String ROUTE_PROVIDER_HEADER = "X-Route-Provider";

    private final TollPlazaSearchService searchService;
    private final TollPlazaResponseMapper mapper;

    public TollPlazaController(TollPlazaSearchService searchService, TollPlazaResponseMapper mapper) {
        this.searchService = searchService;
        this.mapper = mapper;
    }

    @Operation(summary = "List toll plazas between two pincodes",
            description = """
                    Resolves both pincodes to coordinates, computes the driving route, and returns every toll plaza \
                    within 1 km of the route, ordered by driving distance from the source. Results are cached, so \
                    repeating a request is near-instant (see the X-Cache response header).""")
    @ApiResponse(responseCode = "200", description = "Route found (tollPlazas may be empty)",
            headers = {
                    @Header(name = CACHE_HEADER, description = "HIT if served from cache, otherwise MISS"),
                    @Header(name = ROUTE_PROVIDER_HEADER, description = "Routing engine that produced the route")})
    @ApiResponse(responseCode = "400", description = "Invalid input",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                    @ExampleObject(name = "Invalid pincode", value = "{\"error\":\"Invalid source or destination pincode\"}"),
                    @ExampleObject(name = "Same pincodes",
                            value = "{\"error\":\"Source and destination pincodes cannot be the same\"}")}))
    @ApiResponse(responseCode = "422", description = "No drivable route exists between the pincodes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Routing service temporarily unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TollPlazaSearchResponse> findTollPlazas(@Valid @RequestBody TollPlazaSearchRequest request) {
        SearchOutcome outcome = searchService.search(request.sourcePincode(), request.destinationPincode());
        return ResponseEntity.ok()
                .header(CACHE_HEADER, outcome.cacheHit() ? "HIT" : "MISS")
                .header(ROUTE_PROVIDER_HEADER, outcome.result().routeProvider())
                .body(mapper.toResponse(outcome.result()));
    }
}
