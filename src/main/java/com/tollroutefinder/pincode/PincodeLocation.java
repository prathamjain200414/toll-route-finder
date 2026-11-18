package com.tollroutefinder.pincode;

import com.tollroutefinder.geo.GeoPoint;

/**
 * Representative coordinate for an Indian postal pincode.
 */
public record PincodeLocation(String pincode, GeoPoint point, String district, String state) {
}
