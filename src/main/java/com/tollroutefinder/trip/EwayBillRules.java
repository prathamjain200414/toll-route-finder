package com.tollroutefinder.trip;

/**
 * E-way bill validity under CGST Rule 138(10): one day for the first 200 km of road distance, plus one day for
 * every further 200 km or part of it (regular cargo; over-dimensional cargo uses 20 km and is out of scope).
 */
public final class EwayBillRules {

    static final double KM_PER_DAY = 200.0;

    private EwayBillRules() {
    }

    public static int validityDays(double distanceKm) {
        if (distanceKm <= 0) {
            return 1;
        }
        return (int) Math.max(1, Math.ceil(distanceKm / KM_PER_DAY));
    }
}
