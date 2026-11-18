package com.tollroutefinder.api.dto;

import java.util.List;

/**
 * Toll reconciliation report for one trip.
 */
public record TollAuditResponse(RouteSummary route,
                                AuditSummary summary,
                                List<AuditedChargeDto> charges,
                                List<TollPlazaDto> notCharged) {
}
