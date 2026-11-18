package com.tollroutefinder.trip;

import com.tollroutefinder.search.TollPlazaSearchResult;
import com.tollroutefinder.toll.TollOnRoute;

import java.util.List;

/**
 * Result of reconciling a trip's toll charges against the plazas on its planned route.
 *
 * @param trip       the planned route and its expected plazas
 * @param charges    every submitted charge, in the order submitted
 * @param notCharged expected plazas with no matching charge (possible tracking gap or a different road taken)
 */
public record TollAudit(TollPlazaSearchResult trip, List<AuditedCharge> charges, List<TollOnRoute> notCharged) {

    public TollAudit {
        charges = List.copyOf(charges);
        notCharged = List.copyOf(notCharged);
    }

    public long count(AuditStatus status) {
        return charges.stream().filter(c -> c.status() == status).count();
    }

    /** CLEAN when every charge is a first charge on the route and every expected plaza was charged. */
    public boolean clean() {
        return notCharged.isEmpty() && charges.stream().allMatch(c -> c.status() == AuditStatus.ON_ROUTE);
    }
}
