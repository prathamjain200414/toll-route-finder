package com.tollroutefinder.search;

import com.tollroutefinder.config.CacheConfig;
import com.tollroutefinder.pincode.PincodeLocation;
import com.tollroutefinder.pincode.PincodeLocator;
import com.tollroutefinder.routing.Route;
import com.tollroutefinder.routing.RouteProvider;
import com.tollroutefinder.toll.TollOnRoute;
import com.tollroutefinder.toll.TollPlazaMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finds the toll plazas between two pincodes: pincode -> coordinates -> route -> plazas near the route.
 * <p>
 * Results are cached per (source, destination). The cache loads each key at most once at a time, so a burst of
 * identical requests triggers a single routing call. Failures are never cached.
 */
@Service
public class TollPlazaSearchService {

    private static final Logger log = LoggerFactory.getLogger(TollPlazaSearchService.class);

    private final PincodeLocator pincodeLocator;
    private final RouteProvider routeProvider;
    private final TollPlazaMatcher matcher;
    private final Cache cache;

    public TollPlazaSearchService(PincodeLocator pincodeLocator, RouteProvider routeProvider,
                                  TollPlazaMatcher matcher, CacheManager cacheManager) {
        this.pincodeLocator = pincodeLocator;
        this.routeProvider = routeProvider;
        this.matcher = matcher;
        this.cache = Objects.requireNonNull(cacheManager.getCache(CacheConfig.TOLL_SEARCH_CACHE),
                "Cache " + CacheConfig.TOLL_SEARCH_CACHE + " is not configured");
    }

    public SearchOutcome search(String sourcePincode, String destinationPincode) {
        if (sourcePincode.equals(destinationPincode)) {
            throw new SamePincodeException();
        }
        String key = sourcePincode + "->" + destinationPincode;
        AtomicBoolean computed = new AtomicBoolean(false);
        try {
            TollPlazaSearchResult result = cache.get(key, () -> {
                computed.set(true);
                return compute(sourcePincode, destinationPincode);
            });
            return new SearchOutcome(result, !computed.get());
        } catch (Cache.ValueRetrievalException e) {
            // The cache wraps loader exceptions; surface our own domain exceptions unchanged.
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    private TollPlazaSearchResult compute(String sourcePincode, String destinationPincode) {
        long started = System.nanoTime();
        PincodeLocation source = locate(sourcePincode);
        PincodeLocation destination = locate(destinationPincode);
        Route route = routeProvider.route(source.point(), destination.point());
        List<TollOnRoute> tolls = matcher.findOnRoute(route.line());
        log.info("{} -> {}: {} km via {}, {} toll plazas ({} ms)", sourcePincode, destinationPincode,
                Math.round(route.distanceKm()), route.provider(), tolls.size(),
                (System.nanoTime() - started) / 1_000_000);
        return new TollPlazaSearchResult(sourcePincode, destinationPincode, route.distanceKm(), route.provider(), tolls);
    }

    private PincodeLocation locate(String pincode) {
        return pincodeLocator.locate(pincode).orElseThrow(() -> new InvalidPincodeException(pincode));
    }
}
