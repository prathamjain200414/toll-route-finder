package com.tollroutefinder.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.geo.RouteLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Adapter for the OpenRouteService directions API. Uses the {@code driving-hgv} (truck) profile by default:
 * trucks are steered onto national highways, which matches how freight actually moves and which toll plazas a
 * shipment will really pass.
 *
 * @see <a href="https://openrouteservice.org/dev/#/api-docs/v2/directions/{profile}/geojson/post">ORS directions</a>
 */
public class OrsRouteProvider implements RouteProvider {

    private static final Logger log = LoggerFactory.getLogger(OrsRouteProvider.class);

    /** ORS error codes meaning "the request was fine but no route/road exists". */
    private static final List<String> NO_ROUTE_CODES = List.of("\"code\":2009", "\"code\":2010");

    /** Pincode centroids can sit away from a road; let ORS snap up to this many metres. */
    private static final int SNAP_RADIUS_METRES = 5000;

    private final RestClient restClient;
    private final String profile;
    private final String apiKey;

    public OrsRouteProvider(RestClient restClient, String profile, String apiKey) {
        this.restClient = restClient;
        this.profile = profile;
        this.apiKey = apiKey;
    }

    @Override
    public Route route(GeoPoint from, GeoPoint to) {
        Map<String, Object> body = Map.of(
                "coordinates", List.of(List.of(from.longitude(), from.latitude()), List.of(to.longitude(), to.latitude())),
                "radiuses", List.of(SNAP_RADIUS_METRES, SNAP_RADIUS_METRES),
                // Toll search is about road routes; a ferry crossing is not one.
                "options", Map.of("avoid_features", List.of("ferries")));
        OrsResponse response;
        try {
            response = restClient.post()
                    .uri("/v2/directions/{profile}/geojson", profile)
                    .header("Authorization", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OrsResponse.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (RestClientException e) {
            throw new RoutingUnavailableException("Routing service could not be reached: " + e.getMessage(), e);
        }
        return toRoute(response);
    }

    @Override
    public String name() {
        return "OpenRouteService (" + profile + ")";
    }

    private static RuntimeException translate(RestClientResponseException e) {
        if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            return new RoutingUnavailableException("Routing service rate limit reached, please retry shortly", e);
        }
        String body = e.getResponseBodyAsString();
        if (NO_ROUTE_CODES.stream().anyMatch(body::contains)) {
            return new RouteNotFoundException("No drivable route exists between the given locations");
        }
        // 401/403 (bad key), 400 (limits) and 5xx: let a fallback engine try.
        log.warn("OpenRouteService returned {}: {}", e.getStatusCode(), body);
        return new RoutingUnavailableException("Routing service returned " + e.getStatusCode().value(), e);
    }

    private Route toRoute(OrsResponse response) {
        if (response == null || response.features() == null) {
            throw new RoutingUnavailableException("Routing service returned an unexpected response");
        }
        if (response.features().isEmpty()) {
            throw new RouteNotFoundException("No drivable route exists between the given locations");
        }
        Feature feature = response.features().getFirst();
        if (feature.geometry() == null || feature.geometry().coordinates() == null
                || feature.geometry().coordinates().size() < 2
                || feature.properties() == null || feature.properties().summary() == null) {
            throw new RoutingUnavailableException("Routing service returned a route without geometry");
        }
        try {
            List<GeoPoint> points = feature.geometry().coordinates().stream()
                    .map(c -> new GeoPoint(c.get(1), c.get(0))) // GeoJSON order is [lon, lat]
                    .toList();
            Summary summary = feature.properties().summary();
            return new Route(new RouteLine(points), summary.distance() / 1000.0, summary.duration() / 60.0, name());
        } catch (RuntimeException e) {
            throw new RoutingUnavailableException("Routing service returned invalid geometry", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrsResponse(List<Feature> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feature(Properties properties, Geometry geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(Summary summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Summary(double distance, double duration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(List<List<Double>> coordinates) {
    }
}
