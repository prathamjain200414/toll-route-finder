package com.tollroutefinder.trip;

/**
 * What a toll charge on a trip (from a FASTag statement or a transporter invoice) turned out to be.
 */
public enum AuditStatus {
    /** The plaza is on the planned route and this is its first charge. */
    ON_ROUTE,
    /** The plaza is on the planned route but was already charged on this trip. */
    DUPLICATE_CHARGE,
    /** The plaza exists but is not on the planned route: the truck deviated, or the charge is wrong. */
    OFF_ROUTE,
    /** No toll plaza with this name exists in the dataset. */
    UNKNOWN_PLAZA
}
