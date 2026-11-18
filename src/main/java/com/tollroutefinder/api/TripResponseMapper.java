package com.tollroutefinder.api;

import com.tollroutefinder.api.dto.AuditSummary;
import com.tollroutefinder.api.dto.AuditedChargeDto;
import com.tollroutefinder.api.dto.GeoJsonLineString;
import com.tollroutefinder.api.dto.RouteSummary;
import com.tollroutefinder.api.dto.TollAuditResponse;
import com.tollroutefinder.api.dto.TollPlazaDto;
import com.tollroutefinder.api.dto.TripPlanResponse;
import com.tollroutefinder.api.dto.TripSummary;
import com.tollroutefinder.api.dto.TripTollPlazaDto;
import com.tollroutefinder.search.TollPlazaSearchResult;
import com.tollroutefinder.toll.TollOnRoute;
import com.tollroutefinder.trip.AuditStatus;
import com.tollroutefinder.trip.AuditedCharge;
import com.tollroutefinder.trip.EwayBillRules;
import com.tollroutefinder.trip.TollAudit;
import org.springframework.stereotype.Component;

import java.util.Locale;

import static com.tollroutefinder.api.TollPlazaResponseMapper.round;

@Component
public class TripResponseMapper {

    public TripPlanResponse toPlan(TollPlazaSearchResult result) {
        double hours = result.routeDurationMinutes() / 60.0;
        TripSummary summary = new TripSummary(result.sourcePincode(), result.destinationPincode(),
                round(result.routeDistanceKm()), round(hours), EwayBillRules.validityDays(result.routeDistanceKm()),
                result.tolls().size(), result.routeProvider());
        var tolls = result.tolls().stream().map(t -> new TripTollPlazaDto(t.plaza().name(),
                t.plaza().location().latitude(), t.plaza().location().longitude(), round(t.distanceFromSourceKm()),
                round(etaHours(t, result)))).toList();
        var path = GeoJsonLineString.of(result.simplifiedPath().stream()
                .map(p -> new double[]{round5(p.longitude()), round5(p.latitude())}).toList());
        return new TripPlanResponse(summary, tolls, path);
    }

    /** Assumes the route's average pace applies throughout; good enough for planning, not a live ETA. */
    private static double etaHours(TollOnRoute toll, TollPlazaSearchResult result) {
        if (result.routeDistanceKm() <= 0) {
            return 0;
        }
        double fraction = Math.min(1, toll.distanceFromSourceKm() / result.routeDistanceKm());
        return fraction * result.routeDurationMinutes() / 60.0;
    }

    public TollAuditResponse toAudit(TollAudit audit) {
        TollPlazaSearchResult trip = audit.trip();
        AuditSummary summary = new AuditSummary(audit.clean() ? "CLEAN" : "REVIEW", trip.tolls().size(),
                audit.charges().size(), audit.count(AuditStatus.ON_ROUTE), audit.count(AuditStatus.DUPLICATE_CHARGE),
                audit.count(AuditStatus.OFF_ROUTE), audit.count(AuditStatus.UNKNOWN_PLAZA), audit.notCharged().size());
        return new TollAuditResponse(
                new RouteSummary(trip.sourcePincode(), trip.destinationPincode(), round(trip.routeDistanceKm())),
                summary,
                audit.charges().stream().map(TripResponseMapper::toDto).toList(),
                audit.notCharged().stream().map(t -> new TollPlazaDto(t.plaza().name(), t.plaza().location().latitude(),
                        t.plaza().location().longitude(), round(t.distanceFromSourceKm()))).toList());
    }

    private static AuditedChargeDto toDto(AuditedCharge charge) {
        var plaza = charge.plaza();
        return new AuditedChargeDto(charge.chargedName(), charge.status().name(),
                plaza == null ? null : plaza.name(),
                plaza == null ? null : plaza.location().latitude(),
                plaza == null ? null : plaza.location().longitude(),
                charge.distanceFromSourceKm() == null ? null : round(charge.distanceFromSourceKm()),
                charge.distanceFromRouteKm() == null ? null : round(charge.distanceFromRouteKm()),
                note(charge));
    }

    private static String note(AuditedCharge charge) {
        return switch (charge.status()) {
            case ON_ROUTE -> "Matches a toll plaza on the planned route";
            case DUPLICATE_CHARGE -> "Already charged on this trip: possible double deduction";
            case OFF_ROUTE -> charge.distanceFromRouteKm() == null
                    ? "Toll plaza is not on the planned route"
                    : String.format(Locale.ROOT, "Toll plaza is %.1f km from the planned route: route deviation "
                    + "or wrong charge", charge.distanceFromRouteKm());
            case UNKNOWN_PLAZA -> "No toll plaza with this name in the dataset";
        };
    }

    private static double round5(double degrees) {
        return Math.round(degrees * 100_000) / 100_000.0;
    }
}
