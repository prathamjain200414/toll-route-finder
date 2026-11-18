package com.tollroutefinder.toll;

import com.tollroutefinder.config.TollFinderProperties;
import com.tollroutefinder.geo.RouteLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Decides which toll plazas lie on a route: a plaza is on the route when its coordinate is within
 * {@code maxDistanceFromRouteKm} of the route line. Results are ordered by distance from the source.
 */
@Component
public class TollPlazaMatcher {

    private final TollPlazaIndex index;
    private final double maxDistanceFromRouteKm;

    @Autowired
    public TollPlazaMatcher(TollPlazaIndex index, TollFinderProperties properties) {
        this(index, properties.matching().maxDistanceFromRouteKm());
    }

    public TollPlazaMatcher(TollPlazaIndex index, double maxDistanceFromRouteKm) {
        this.index = index;
        this.maxDistanceFromRouteKm = maxDistanceFromRouteKm;
    }

    public List<TollOnRoute> findOnRoute(RouteLine route) {
        return index.candidatesNear(route, maxDistanceFromRouteKm).stream()
                .map(plaza -> {
                    RouteLine.Projection projection = route.project(plaza.location());
                    return new TollOnRoute(plaza, projection.distanceAlongKm(), projection.distanceFromLineKm());
                })
                .filter(t -> t.distanceFromRouteKm() <= maxDistanceFromRouteKm)
                .sorted(Comparator.comparingDouble(TollOnRoute::distanceFromSourceKm))
                .toList();
    }
}
