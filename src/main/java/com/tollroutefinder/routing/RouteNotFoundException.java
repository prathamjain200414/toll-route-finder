package com.tollroutefinder.routing;

/**
 * The routing engine answered, but there is no drivable route between the points (e.g. an island pincode).
 */
public class RouteNotFoundException extends RuntimeException {

    public RouteNotFoundException(String message) {
        super(message);
    }
}
