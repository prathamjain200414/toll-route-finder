package com.tollroutefinder.routing;

import com.tollroutefinder.geo.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

class OsrmRouteProviderTest {

    private static final GeoPoint BENGALURU = new GeoPoint(13.114503, 77.574411);
    private static final GeoPoint PUNE = new GeoPoint(18.56995, 73.76615);

    private MockRestServiceServer server;
    private OsrmRouteProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://osrm.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OsrmRouteProvider(builder.build(), "driving");
    }

    @Test
    void requestsFullGeometryWithLongitudeLatitudeOrder() {
        server.expect(requestTo(startsWith("https://osrm.test/route/v1/driving/77.574411,13.114503;73.76615,18.56995")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(queryParam("overview", "full"))
                .andExpect(queryParam("geometries", "geojson"))
                .andExpect(queryParam("steps", "true"))
                .andRespond(withSuccess("""
                        {"code":"Ok","routes":[{"distance":857123.4,"duration":37260.0,
                          "geometry":{"type":"LineString","coordinates":[[77.574411,13.114503],[76.0,15.0],[73.76615,18.56995]]}}]}
                        """, MediaType.APPLICATION_JSON));

        Route route = provider.route(BENGALURU, PUNE);

        assertThat(route.distanceKm()).isCloseTo(857.12, within(0.01));
        assertThat(route.durationMinutes()).isCloseTo(621.0, within(0.01));
        assertThat(route.line().points()).hasSize(3);
        assertThat(route.line().points().get(1)).isEqualTo(new GeoPoint(15.0, 76.0));
        server.verify();
    }

    @Test
    void routeThatNeedsAFerryIsNotARoadRoute() {
        // e.g. Port Blair -> Chennai: OSRM happily returns a 3,900 km route via the Andaman ferry
        server.expect(requestTo(startsWith("https://osrm.test/route/v1/driving/")))
                .andRespond(withSuccess("""
                        {"code":"Ok","routes":[{"distance":3888161.2,"duration":183847.6,
                          "geometry":{"type":"LineString","coordinates":[[92.72,11.62],[88.3,22.5],[80.27,13.08]]},
                          "legs":[{"steps":[{"mode":"driving"},{"mode":"ferry"},{"mode":"driving"}]}]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.route(new GeoPoint(11.62, 92.72), new GeoPoint(13.08, 80.27)))
                .isInstanceOf(RouteNotFoundException.class)
                .hasMessageContaining("ferry");
    }

    @Test
    void noRouteFoundIsReportedAsRouteNotFound() {
        server.expect(requestTo(startsWith("https://osrm.test/route/v1/driving/")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"NoRoute\",\"message\":\"Impossible route between points\"}"));

        assertThatThrownBy(() -> provider.route(BENGALURU, new GeoPoint(11.62, 92.72)))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void okResponseWithoutRoutesIsRouteNotFound() {
        server.expect(requestTo(startsWith("https://osrm.test/route/v1/driving/")))
                .andRespond(withSuccess("{\"code\":\"Ok\",\"routes\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void rateLimitingIsReportedAsUnavailable() {
        server.expect(requestTo(startsWith("https://osrm.test/route/v1/driving/")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE))
                .isInstanceOf(RoutingUnavailableException.class)
                .hasMessageContaining("rate");
    }

    @Test
    void serverErrorIsReportedAsUnavailable() {
        server.expect(requestTo(startsWith("https://osrm.test/route/v1/driving/"))).andRespond(withServerError());

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void malformedResponseIsReportedAsUnavailable() {
        server.expect(requestTo(startsWith("https://osrm.test/route/v1/driving/")))
                .andRespond(withSuccess("{\"code\":\"Ok\",\"routes\":[{\"distance\":1,\"duration\":1,\"geometry\":null}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RoutingUnavailableException.class);
    }
}
