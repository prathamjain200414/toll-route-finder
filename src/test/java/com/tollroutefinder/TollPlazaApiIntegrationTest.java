package com.tollroutefinder;

import com.tollroutefinder.config.CacheConfig;
import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.routing.OsrmRouteProvider;
import com.tollroutefinder.routing.Route;
import com.tollroutefinder.routing.RouteProvider;
import com.tollroutefinder.toll.TollPlazaIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through the real application (startup CSV import into the database, spatial index, pincode dataset,
 * matcher, cache, controller), with routing replayed from a recorded OSRM response for Bengaluru 560064 to
 * Pune 411045, so the test is deterministic and offline.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TollPlazaApiIntegrationTest {

    private static final String BODY = "{\"sourcePincode\":\"560064\",\"destinationPincode\":\"411045\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TollPlazaIndex index;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private RouteProvider routeProvider;

    @BeforeEach
    void setUp() throws IOException {
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.TOLL_SEARCH_CACHE)).clear();
        when(routeProvider.route(any(), any())).thenReturn(recordedBengaluruPuneRoute());
    }

    private static Route recordedBengaluruPuneRoute() throws IOException {
        String json = new ClassPathResource("fixtures/osrm-560064-411045.json").getContentAsString(StandardCharsets.UTF_8);
        RestClient.Builder builder = RestClient.builder().baseUrl("https://osrm.replay");
        MockRestServiceServer.bindTo(builder).build().expect(anything()).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        return new OsrmRouteProvider(builder.build(), "driving")
                .route(new GeoPoint(13.114503, 77.574411), new GeoPoint(18.56995, 73.76615));
    }

    @Test
    void startupImportsAndIndexesTheCleanedTollData() {
        assertThat(index.size()).isEqualTo(1447);
    }

    @Test
    void findsTheTollPlazasOnTheBengaluruPuneRoute() throws Exception {
        mvc.perform(post("/api/v1/toll-plazas").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache", "MISS"))
                .andExpect(jsonPath("$.route.sourcePincode").value("560064"))
                .andExpect(jsonPath("$.route.destinationPincode").value("411045"))
                .andExpect(jsonPath("$.route.distanceInKm", closeTo(856.5, 1.0)))
                .andExpect(jsonPath("$.tollPlazas", hasSize(12)))
                .andExpect(jsonPath("$.tollPlazas[*].name", contains(
                        "Kadathanamale Toll Plaza", "Hulikunte Toll Plaza", "Chokkenahalli Toll Plaza",
                        "Karjeevanhally Toll Plaza", "Guilalu Toll Plaza", "Kananakatte Toll Plaza",
                        "Thimmalapura Toll Plaza", "Hitnal Toll Plaza", "Shahapur Toll Plaza",
                        "Vanagera Toll Plaza", "Nagarhalla Toll Plaza", "Bijapur Toll Plaza")))
                .andExpect(jsonPath("$.tollPlazas[0].distanceFromSource", closeTo(12.2, 0.2)))
                .andExpect(jsonPath("$.tollPlazas[11].distanceFromSource", closeTo(521.6, 0.5)));
    }

    @Test
    void repeatedRequestIsServedFromCache() throws Exception {
        mvc.perform(post("/api/v1/toll-plazas").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(header().string("X-Cache", "MISS"));
        mvc.perform(post("/api/v1/toll-plazas").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(header().string("X-Cache", "HIT"))
                .andExpect(jsonPath("$.tollPlazas", hasSize(12)));

        verify(routeProvider, times(1)).route(any(), any());
    }

    @Test
    void wellFormedButNonExistentPincodeIsRejected() throws Exception {
        mvc.perform(post("/api/v1/toll-plazas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"100000\",\"destinationPincode\":\"411045\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"))
                .andExpect(jsonPath("$.details[0]").value("Pincode 100000 was not found"));
    }

    @Test
    void swaggerDocumentationIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Toll Route Finder API"))
                .andExpect(jsonPath("$.paths['/api/v1/toll-plazas'].post").exists());
    }
}
