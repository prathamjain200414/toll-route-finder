package com.tollroutefinder.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.geo.RouteLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Adapter for the OSRM HTTP route service ({@code /route/v1/{profile}/{lon,lat;lon,lat}}).
 *
 * @see <a href="https://project-osrm.org/docs/v5.24.0/api/#route-service">OSRM route service</a>
 */
public class OsrmRouteProvider implements RouteProvider {

    private static final Logger log = LoggerFactory.getLogger(OsrmRouteProvider.class);

    static final String NAME = "OSRM";

    private final RestClient restClient;
    private final String profile;

    public OsrmRouteProvider(RestClient restClient, String profile) {
        this.restClient = restClient;
        this.profile = profile;
    }

    @Override
    public Route route(GeoPoint from, GeoPoint to) {
        OsrmResponse response;
        try {
            // Separators live in the template: as URI variables, ',' and ';' would be percent-encoded.
            response = restClient.get()
                    .uri("/route/v1/{profile}/{fromLon},{fromLat};{toLon},{toLat}"
                                    + "?overview=full&geometries=geojson&steps=true",
                            profile, strip(from.longitude()), strip(from.latitude()),
                            strip(to.longitude()), strip(to.latitude()))
                    .retrieve()
                    .body(OsrmResponse.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (RestClientException e) {
            throw new RoutingUnavailableException("Routing service could not be reached: " + e.getMessage(), e);
        }
        return toRoute(response);
    }

    @Override
    public String name() {
        return NAME;
    }

    private static String strip(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static RuntimeException translate(RestClientResponseException e) {
        if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            return new RoutingUnavailableException("Routing service rate limit reached, please retry shortly", e);
        }
        String body = e.getResponseBodyAsString();
        if (e.getStatusCode().is4xxClientError() && body.contains("\"NoRoute\"")) {
            return new RouteNotFoundException("No drivable route exists between the given locations");
        }
        log.warn("OSRM returned {}: {}", e.getStatusCode(), body);
        return new RoutingUnavailableException("Routing service returned " + e.getStatusCode().value(), e);
    }

    private static Route toRoute(OsrmResponse response) {
        if (response == null || !"Ok".equals(response.code()) || response.routes() == null) {
            throw new RoutingUnavailableException("Routing service returned an unexpected response");
        }
        if (response.routes().isEmpty()) {
            throw new RouteNotFoundException("No drivable route exists between the given locations");
        }
        OsrmRoute best = response.routes().getFirst();
        if (best.geometry() == null || best.geometry().coordinates() == null
                || best.geometry().coordinates().size() < 2) {
            throw new RoutingUnavailableException("Routing service returned a route without geometry");
        }
        if (best.usesFerry()) {
            throw new RouteNotFoundException(
                    "No road route exists between the given locations (the only route requires a ferry)");
        }
        try {
            List<GeoPoint> points = best.geometry().coordinates().stream()
                    .map(c -> new GeoPoint(c.get(1), c.get(0))) // GeoJSON order is [lon, lat]
                    .toList();
            return new Route(new RouteLine(points), best.distance() / 1000.0, best.duration() / 60.0, NAME);
        } catch (RuntimeException e) {
            throw new RoutingUnavailableException("Routing service returned invalid geometry", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmResponse(String code, String message, List<OsrmRoute> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmRoute(double distance, double duration, OsrmGeometry geometry, List<OsrmLeg> legs) {

        /** The public OSRM server cannot exclude ferries, so detect them from the step travel modes. */
        boolean usesFerry() {
            return legs != null && legs.stream()
                    .filter(leg -> leg.steps() != null)
                    .flatMap(leg -> leg.steps().stream())
                    .anyMatch(step -> "ferry".equals(step.mode()));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmLeg(List<OsrmStep> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmStep(String mode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmGeometry(String type, List<List<Double>> coordinates) {
    }
}
