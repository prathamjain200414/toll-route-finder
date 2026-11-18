package com.tollroutefinder;

import com.tollroutefinder.pincode.OfflinePincodeLocator;
import com.tollroutefinder.pincode.PincodeLocation;
import com.tollroutefinder.routing.OsrmRouteProvider;
import com.tollroutefinder.routing.Route;
import com.tollroutefinder.toll.TollOnRoute;
import com.tollroutefinder.toll.TollPlazaCsvReader;
import com.tollroutefinder.toll.TollPlazaDeduplicator;
import com.tollroutefinder.toll.TollPlazaIndex;
import com.tollroutefinder.toll.TollPlazaMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the whole pipeline against the real public OSRM server for the benchmark route given in the assignment
 * (Bengaluru 560064 -> Pune 411045; Mappls lists 15 tolls via NH48 and 14 via NH50).
 * <p>
 * Needs internet, so it is opt-in: {@code ./mvnw test -Dtest=LiveRouteAccuracyTest -Dlive=true}
 */
@EnabledIfSystemProperty(named = "live", matches = "true")
class LiveRouteAccuracyTest {

    @Test
    void bengaluruToPune() {
        OfflinePincodeLocator pincodes = new OfflinePincodeLocator(new ClassPathResource("data/pincodes.csv"));
        TollPlazaIndex index = new TollPlazaIndex();
        index.replaceAll(new TollPlazaDeduplicator(0.3, 2.0)
                .deduplicate(new TollPlazaCsvReader().read(new ClassPathResource("data/toll_plazas.csv")).rows())
                .plazas());
        TollPlazaMatcher matcher = new TollPlazaMatcher(index, 1.0);
        OsrmRouteProvider osrm = new OsrmRouteProvider(
                RestClient.builder().baseUrl("https://router.project-osrm.org")
                        .defaultHeader("Accept-Encoding", "gzip").build(), "driving");

        PincodeLocation from = pincodes.locate("560064").orElseThrow();
        PincodeLocation to = pincodes.locate("411045").orElseThrow();
        Route route = osrm.route(from.point(), to.point());
        List<TollOnRoute> tolls = matcher.findOnRoute(route.line());

        System.out.printf(Locale.ROOT, "Route %.1f km, %d points, %d tolls%n",
                route.distanceKm(), route.line().points().size(), tolls.size());
        tolls.forEach(t -> System.out.printf(Locale.ROOT, "  %7.1f km  %-40s (%.2f km off route)%n",
                t.distanceFromSourceKm(), t.plaza().name(), t.distanceFromRouteKm()));

        assertThat(route.distanceKm()).isBetween(800.0, 950.0);
        assertThat(tolls).isNotEmpty();
    }
}
