package com.tollroutefinder.routing;

import com.tollroutefinder.geo.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OrsRouteProviderTest {

    private static final GeoPoint BENGALURU = new GeoPoint(13.114503, 77.574411);
    private static final GeoPoint PUNE = new GeoPoint(18.56995, 73.76615);
    private static final String URL = "https://ors.test/v2/directions/driving-hgv/geojson";

    private MockRestServiceServer server;
    private OrsRouteProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ors.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OrsRouteProvider(builder.build(), "driving-hgv", "secret-key");
    }

    @Test
    void postsCoordinatesInLonLatOrderWithApiKey() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "secret-key"))
                .andExpect(content().json("{\"coordinates\":[[77.574411,13.114503],[73.76615,18.56995]]}"))
                .andRespond(withSuccess("""
                        {"type":"FeatureCollection","features":[{"type":"Feature",
                          "properties":{"summary":{"distance":851800.0,"duration":58000.0}},
                          "geometry":{"type":"LineString","coordinates":[[77.574411,13.114503],[75.0,16.0],[73.76615,18.56995]]}}]}
                        """, MediaType.APPLICATION_JSON));

        Route route = provider.route(BENGALURU, PUNE);

        assertThat(route.provider()).isEqualTo("OpenRouteService (driving-hgv)");
        assertThat(route.distanceKm()).isCloseTo(851.8, within(0.01));
        assertThat(route.durationMinutes()).isCloseTo(966.67, within(0.01));
        assertThat(route.line().points()).hasSize(3);
        assertThat(route.line().points().get(1)).isEqualTo(new GeoPoint(16.0, 75.0));
        server.verify();
    }

    @Test
    void routeNotFoundErrorCodeIsRouteNotFound() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":2009,\"message\":\"Route could not be found\"}}"));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void unroutablePointErrorCodeIsRouteNotFound() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":2010,\"message\":\"Could not find routable point\"}}"));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void invalidKeyIsUnavailableSoTheFallbackCanTakeOver() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void quotaExceededIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE))
                .isInstanceOf(RoutingUnavailableException.class)
                .hasMessageContaining("rate");
    }

    @Test
    void emptyFeatureCollectionIsRouteNotFound() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RouteNotFoundException.class);
    }
}
