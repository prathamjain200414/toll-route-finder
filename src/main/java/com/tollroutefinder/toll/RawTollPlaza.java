package com.tollroutefinder.toll;

import com.tollroutefinder.geo.GeoPoint;

/**
 * A toll plaza exactly as read from the source CSV, before cleaning. The name may be blank or "nan".
 */
public record RawTollPlaza(String name, GeoPoint location, String state) {
}
