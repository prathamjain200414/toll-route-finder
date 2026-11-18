package com.tollroutefinder.routing;

/**
 * The routing engine could not be used: network failure, timeout, rate limiting, 5xx or an unparseable response.
 * This is not the caller's fault and is typically transient.
 */
public class RoutingUnavailableException extends RuntimeException {

    public RoutingUnavailableException(String message) {
        super(message);
    }

    public RoutingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
