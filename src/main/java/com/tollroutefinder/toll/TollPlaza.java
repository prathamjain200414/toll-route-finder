package com.tollroutefinder.toll;

import com.tollroutefinder.geo.GeoPoint;

/**
 * A cleaned, de-duplicated toll plaza.
 *
 * @param key      stable natural key (normalised name + rounded coordinates) used for idempotent upserts
 * @param name     display name
 * @param location coordinates
 * @param state    Indian state the plaza is in, as given by the source data
 */
public record TollPlaza(String key, String name, GeoPoint location, String state) {

    public static final String UNNAMED = "Unnamed Toll Plaza";
}
