package com.tollroutefinder.trip;

import com.tollroutefinder.config.TollFinderProperties;
import com.tollroutefinder.geo.RouteLine;
import com.tollroutefinder.search.TollPlazaSearchResult;
import com.tollroutefinder.search.TollPlazaSearchService;
import com.tollroutefinder.toll.PlazaNames;
import com.tollroutefinder.toll.TollOnRoute;
import com.tollroutefinder.toll.TollPlaza;
import com.tollroutefinder.toll.TollPlazaIndex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Reconciles the toll charges billed for a one-way trip (FASTag statement lines or transporter invoice items)
 * against the toll plazas on the planned route between its pincodes.
 * <p>
 * Each charge is matched by name, tolerating the spelling differences between data sources. A charge is
 * {@link AuditStatus#ON_ROUTE} the first time a route plaza is matched and {@link AuditStatus#DUPLICATE_CHARGE}
 * after that. A name that only matches plazas away from the route is {@link AuditStatus#OFF_ROUTE}, reported with
 * the distance of the nearest such plaza; a name matching nothing is {@link AuditStatus#UNKNOWN_PLAZA}. Route
 * plazas that no charge matched are returned as not charged.
 * <p>
 * The route comes from the same cached search as {@code /api/v1/toll-plazas}, so auditing a lane that has
 * already been searched needs no routing call.
 */
@Service
public class TollAuditService {

    private final TollPlazaSearchService searchService;
    private final TollPlazaIndex index;
    private final double maxDistanceFromRouteKm;

    @Autowired
    public TollAuditService(TollPlazaSearchService searchService, TollPlazaIndex index, TollFinderProperties properties) {
        this(searchService, index, properties.matching().maxDistanceFromRouteKm());
    }

    public TollAuditService(TollPlazaSearchService searchService, TollPlazaIndex index, double maxDistanceFromRouteKm) {
        this.searchService = searchService;
        this.index = index;
        this.maxDistanceFromRouteKm = maxDistanceFromRouteKm;
    }

    public TollAudit audit(String sourcePincode, String destinationPincode, List<String> chargedPlazaNames) {
        TollPlazaSearchResult trip = searchService.search(sourcePincode, destinationPincode).result();
        List<TollOnRoute> expected = trip.tolls();
        List<String> expectedNames = expected.stream().map(t -> PlazaNames.normalise(t.plaza().name())).toList();
        boolean[] charged = new boolean[expected.size()];
        RouteLine path = trip.simplifiedPath().size() >= 2 ? new RouteLine(trip.simplifiedPath()) : null;

        List<AuditedCharge> charges = new ArrayList<>();
        for (String chargedName : chargedPlazaNames) {
            String name = PlazaNames.normalise(chargedName);
            int first = bestMatch(expectedNames, name, charged, false);
            if (first >= 0) {
                charged[first] = true;
                charges.add(onRoute(chargedName, AuditStatus.ON_ROUTE, expected.get(first)));
                continue;
            }
            int repeat = bestMatch(expectedNames, name, charged, true);
            if (repeat >= 0) {
                charges.add(onRoute(chargedName, AuditStatus.DUPLICATE_CHARGE, expected.get(repeat)));
                continue;
            }
            charges.add(offRouteOrUnknown(chargedName, name, path));
        }

        List<TollOnRoute> notCharged = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            if (!charged[i]) {
                notCharged.add(expected.get(i));
            }
        }
        return new TollAudit(trip, charges, notCharged);
    }

    /**
     * Index of the closest-named expected plaza whose charged flag equals {@code alreadyCharged}, or -1.
     * Ties go to the earliest plaza on the route.
     */
    private static int bestMatch(List<String> expectedNames, String name, boolean[] charged, boolean alreadyCharged) {
        int best = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < expectedNames.size(); i++) {
            if (charged[i] != alreadyCharged) {
                continue;
            }
            OptionalInt score = PlazaNames.lenientMatchScore(expectedNames.get(i), name);
            if (score.isPresent() && score.getAsInt() < bestScore) {
                best = i;
                bestScore = score.getAsInt();
            }
        }
        return best;
    }

    private static AuditedCharge onRoute(String chargedName, AuditStatus status, TollOnRoute toll) {
        return new AuditedCharge(chargedName, status, toll.plaza(), toll.distanceFromSourceKm(), null);
    }

    private AuditedCharge offRouteOrUnknown(String chargedName, String name, RouteLine path) {
        TollPlaza nearest = null;
        double nearestKm = Double.MAX_VALUE;
        for (TollPlaza plaza : index.all()) {
            if (PlazaNames.lenientMatchScore(PlazaNames.normalise(plaza.name()), name).isEmpty()) {
                continue;
            }
            double km = path == null ? Double.MAX_VALUE : path.project(plaza.location()).distanceFromLineKm();
            if (nearest == null || km < nearestKm) {
                nearest = plaza;
                nearestKm = km;
            }
        }
        if (nearest == null) {
            return new AuditedCharge(chargedName, AuditStatus.UNKNOWN_PLAZA, null, null, null);
        }
        if (nearestKm <= maxDistanceFromRouteKm) {
            // Within tolerance of the (simplified) route although the full-resolution match did not list it:
            // treat as on route rather than accuse the trip of a deviation.
            RouteLine.Projection projection = path.project(nearest.location());
            return new AuditedCharge(chargedName, AuditStatus.ON_ROUTE, nearest, projection.distanceAlongKm(), null);
        }
        return new AuditedCharge(chargedName, AuditStatus.OFF_ROUTE, nearest, null,
                path == null ? null : nearestKm);
    }
}
