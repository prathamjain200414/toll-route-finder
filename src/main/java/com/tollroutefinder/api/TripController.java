package com.tollroutefinder.api;

import com.tollroutefinder.api.dto.ErrorResponse;
import com.tollroutefinder.api.dto.TollAuditRequest;
import com.tollroutefinder.api.dto.TollAuditResponse;
import com.tollroutefinder.api.dto.TollPlazaSearchRequest;
import com.tollroutefinder.api.dto.TripPlanResponse;
import com.tollroutefinder.search.SearchOutcome;
import com.tollroutefinder.search.TollPlazaSearchService;
import com.tollroutefinder.trip.TollAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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

/**
 * Beyond the assignment: what the toll plaza search enables for a freight platform. Both endpoints reuse the
 * cached search behind {@code /api/v1/toll-plazas}.
 */
@RestController
@RequestMapping("/api/v1/trips")
@Tag(name = "Trips (extensions)", description = "Trip planning and toll charge reconciliation built on the toll plaza search")
@ApiResponse(responseCode = "400", description = "Invalid input",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "422", description = "No drivable route exists between the pincodes",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "503", description = "Routing service temporarily unavailable",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class TripController {

    private final TollPlazaSearchService searchService;
    private final TollAuditService auditService;
    private final TripResponseMapper mapper;

    public TripController(TollPlazaSearchService searchService, TollAuditService auditService, TripResponseMapper mapper) {
        this.searchService = searchService;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    @Operation(summary = "Plan a trip: tolls with ETAs, drive time, e-way bill validity and route geometry",
            description = """
                    Same search as POST /api/v1/toll-plazas, plus: estimated driving hours, the estimated hour each \
                    toll plaza is reached, e-way bill validity days (CGST Rule 138(10)), and the route as a GeoJSON \
                    LineString. Powers the map at /.""")
    @ApiResponse(responseCode = "200", description = "Trip planned")
    @PostMapping(path = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TripPlanResponse> plan(@Valid @RequestBody TollPlazaSearchRequest request) {
        SearchOutcome outcome = searchService.search(request.sourcePincode(), request.destinationPincode());
        return ResponseEntity.ok()
                .header(TollPlazaController.CACHE_HEADER, outcome.cacheHit() ? "HIT" : "MISS")
                .header(TollPlazaController.ROUTE_PROVIDER_HEADER, outcome.result().routeProvider())
                .body(mapper.toPlan(outcome.result()));
    }

    @Operation(summary = "Audit a trip's toll charges against its planned route",
            description = """
                    Reconciles the toll plazas a one-way trip was charged for (FASTag statement or transporter \
                    invoice) with the plazas on the planned route. Names are matched despite spelling differences. \
                    Each charge is ON_ROUTE, DUPLICATE_CHARGE (possible double deduction), OFF_ROUTE (route \
                    deviation or wrong charge, with its distance from the route) or UNKNOWN_PLAZA; route plazas that \
                    were never charged are listed too. The verdict is CLEAN or REVIEW.""")
    @ApiResponse(responseCode = "200", description = "Audit completed")
    @PostMapping(path = "/toll-audit", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TollAuditResponse audit(@Valid @RequestBody TollAuditRequest request) {
        return mapper.toAudit(auditService.audit(request.sourcePincode(), request.destinationPincode(),
                request.chargedTollPlazas()));
    }
}
