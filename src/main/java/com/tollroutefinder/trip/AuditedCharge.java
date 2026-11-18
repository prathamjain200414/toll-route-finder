package com.tollroutefinder.trip;

import com.tollroutefinder.toll.TollPlaza;

/**
 * One audited toll charge.
 *
 * @param chargedName          plaza name as it appeared on the statement
 * @param status               audit outcome
 * @param plaza                the plaza the name was matched to, or {@code null} for {@link AuditStatus#UNKNOWN_PLAZA}
 * @param distanceFromSourceKm route chainage of the plaza, for plazas on the route; otherwise {@code null}
 * @param distanceFromRouteKm  for {@link AuditStatus#OFF_ROUTE}, how far the plaza is from the planned route
 */
public record AuditedCharge(String chargedName,
                            AuditStatus status,
                            TollPlaza plaza,
                            Double distanceFromSourceKm,
                            Double distanceFromRouteKm) {
}
