package com.tollroutefinder.trip;

import com.tollroutefinder.config.CacheConfig;
import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.geo.RouteLine;
import com.tollroutefinder.pincode.PincodeLocation;
import com.tollroutefinder.pincode.PincodeLocator;
import com.tollroutefinder.routing.Route;
import com.tollroutefinder.routing.RouteProvider;
import com.tollroutefinder.search.TollPlazaSearchService;
import com.tollroutefinder.toll.TollPlaza;
import com.tollroutefinder.toll.TollPlazaIndex;
import com.tollroutefinder.toll.TollPlazaMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TollAuditServiceTest {

    private static final GeoPoint SOURCE = new GeoPoint(10.0, 77.0);
    private static final GeoPoint DESTINATION = new GeoPoint(12.0, 77.0);

    private TollAuditService service;

    @BeforeEach
    void setUp() {
        PincodeLocator pincodes = mock(PincodeLocator.class);
        RouteProvider routes = mock(RouteProvider.class);
        when(pincodes.locate("600001")).thenReturn(Optional.of(new PincodeLocation("600001", SOURCE, "D", "S")));
        when(pincodes.locate("600002")).thenReturn(Optional.of(new PincodeLocation("600002", DESTINATION, "D", "S")));
        List<GeoPoint> points = new ArrayList<>();
        for (double lat = 10.0; lat <= 12.0 + 1e-9; lat += 0.01) {
            points.add(new GeoPoint(lat, 77.0));
        }
        when(routes.route(SOURCE, DESTINATION)).thenReturn(new Route(new RouteLine(points), 222.4, 180, "Test"));

        TollPlazaIndex index = new TollPlazaIndex();
        index.replaceAll(List.of(
                new TollPlaza("a", "Kulumapalya Toll Plaza", new GeoPoint(10.5, 77.001), "S"),
                new TollPlaza("b", "Hitnal Toll Plaza", new GeoPoint(11.5, 77.0), "S"),
                // Same name as a plaza far away: the one near the route must be preferred.
                new TollPlaza("c", "Patas Toll Plaza", new GeoPoint(11.0, 77.3), "S"),
                new TollPlaza("d", "Patas Toll Plaza", new GeoPoint(20.0, 80.0), "S")));
        TollPlazaSearchService search = new TollPlazaSearchService(pincodes, routes, new TollPlazaMatcher(index, 1.0),
                new ConcurrentMapCacheManager(CacheConfig.TOLL_SEARCH_CACHE));
        service = new TollAuditService(search, index, 1.0);
    }

    private TollAudit audit(String... charged) {
        return service.audit("600001", "600002", List.of(charged));
    }

    @Test
    void cleanWhenEveryRoutePlazaIsChargedOnce() {
        TollAudit audit = audit("Kulumepalya", "HITNAL toll plaza");

        assertThat(audit.charges()).extracting(AuditedCharge::status).containsOnly(AuditStatus.ON_ROUTE);
        assertThat(audit.charges().get(0).plaza().name()).isEqualTo("Kulumapalya Toll Plaza");
        assertThat(audit.charges().get(0).distanceFromSourceKm()).isCloseTo(55.6, within(0.5));
        assertThat(audit.notCharged()).isEmpty();
        assertThat(audit.clean()).isTrue();
    }

    @Test
    void secondChargeAtTheSamePlazaIsADuplicate() {
        TollAudit audit = audit("Hitnal", "Kulumapalya", "Hitnal");

        assertThat(audit.charges()).extracting(AuditedCharge::status)
                .containsExactly(AuditStatus.ON_ROUTE, AuditStatus.ON_ROUTE, AuditStatus.DUPLICATE_CHARGE);
        assertThat(audit.clean()).isFalse();
    }

    @Test
    void plazaAwayFromTheRouteIsOffRouteWithItsDistance() {
        AuditedCharge charge = audit("Patas").charges().get(0);

        assertThat(charge.status()).isEqualTo(AuditStatus.OFF_ROUTE);
        assertThat(charge.plaza().key()).isEqualTo("c");
        assertThat(charge.distanceFromRouteKm()).isCloseTo(32.8, within(0.5));
    }

    @Test
    void unknownNameIsReported() {
        AuditedCharge charge = audit("Nowhere Junction").charges().get(0);

        assertThat(charge.status()).isEqualTo(AuditStatus.UNKNOWN_PLAZA);
        assertThat(charge.plaza()).isNull();
    }

    @Test
    void routePlazasWithoutAChargeAreListedInRouteOrder() {
        TollAudit audit = audit();

        assertThat(audit.notCharged()).extracting(t -> t.plaza().name())
                .containsExactly("Kulumapalya Toll Plaza", "Hitnal Toll Plaza");
        assertThat(audit.clean()).isFalse();
    }
}
