package com.tollroutefinder.routing;

import com.tollroutefinder.geo.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Decorator that retries transient failures ({@link RoutingUnavailableException}) with linear backoff.
 * A definitive "no route" answer is never retried.
 */
public class RetryingRouteProvider implements RouteProvider {

    private static final Logger log = LoggerFactory.getLogger(RetryingRouteProvider.class);

    private final RouteProvider delegate;
    private final int maxAttempts;
    private final Duration backoff;

    public RetryingRouteProvider(RouteProvider delegate, int maxAttempts, Duration backoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.delegate = delegate;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    @Override
    public Route route(GeoPoint from, GeoPoint to) {
        for (int attempt = 1; ; attempt++) {
            try {
                return delegate.route(from, to);
            } catch (RoutingUnavailableException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                log.warn("{} attempt {}/{} failed: {}; retrying", delegate.name(), attempt, maxAttempts, e.getMessage());
                sleep(backoff.multipliedBy(attempt));
            }
        }
    }

    @Override
    public String name() {
        return delegate.name();
    }

    private static void sleep(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RoutingUnavailableException("Interrupted while waiting to retry routing", e);
        }
    }
}
