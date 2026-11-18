package com.tollroutefinder;

import com.tollroutefinder.config.CacheConfig;
import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.routing.OsrmRouteProvider;
import com.tollroutefinder.routing.Route;
import com.tollroutefinder.routing.RouteProvider;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
/**
 * The trip extensions end to end, on the same recorded Bengaluru 560064 to Pune 411045 route as
 * {@link TollPlazaApiIntegrationTest}.
 */
class TripApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private RouteProvider routeProvider;

    @BeforeEach
    void setUp() throws IOException {
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.TOLL_SEARCH_CACHE)).clear();
        when(routeProvider.route(any(), any())).thenReturn(recordedBengaluruPuneRoute());
    }

    static Route recordedBengaluruPuneRoute() throws IOException {
        String json = new ClassPathResource("fixtures/osrm-560064-411045.json").getContentAsString(StandardCharsets.UTF_8);
        RestClient.Builder builder = RestClient.builder().baseUrl("https://osrm.replay");
        MockRestServiceServer.bindTo(builder).build().expect(anything()).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        return new OsrmRouteProvider(builder.build(), "driving")
                .route(new GeoPoint(13.114503, 77.574411), new GeoPoint(18.56995, 73.76615));
    }

    @Test
    void planAddsDriveTimeEtasEwayBillValidityAndGeometry() throws Exception {
        mvc.perform(post("/api/v1/trips/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"560064\",\"destinationPincode\":\"411045\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache", "MISS"))
                .andExpect(jsonPath("$.route.distanceInKm", closeTo(856.5, 1.0)))
                .andExpect(jsonPath("$.route.ewayBillValidityDays").value(5))
                .andExpect(jsonPath("$.route.tollPlazaCount").value(12))
                .andExpect(jsonPath("$.route.estimatedDriveHours", closeTo(10.4, 0.1)))
                .andExpect(jsonPath("$.tollPlazas", hasSize(12)))
                .andExpect(jsonPath("$.tollPlazas[11].name").value("Bijapur Toll Plaza"))
                .andExpect(jsonPath("$.tollPlazas[11].etaHoursFromStart", closeTo(6.3, 0.1)))
                .andExpect(jsonPath("$.path.type").value("LineString"))
                .andExpect(jsonPath("$.path.coordinates.length()", lessThan(1000)))
                .andExpect(jsonPath("$.path.coordinates[0][0]", closeTo(77.57, 0.05)))
                .andExpect(jsonPath("$.path.coordinates[0][1]", closeTo(13.11, 0.05)));
    }

    @Test
    void auditReconcilesChargesInRealWorldSpellingsAgainstThePlannedRoute() throws Exception {
        mvc.perform(post("/api/v1/trips/toll-audit").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sourcePincode":"560064","destinationPincode":"411045","chargedTollPlazas":[
                         "Kadathanamale","Hulikunte","Chokkanahalli","Karajeevanahalli","Guilalu","Kannanakatte",
                         "Thimmalapura","Hitnal","Hitnal","Shahapur","Vanagari","Kasaba Bijapur","Patas",
                         "Imaginary Plaza"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.verdict").value("REVIEW"))
                .andExpect(jsonPath("$.summary.expectedTollPlazas").value(12))
                .andExpect(jsonPath("$.summary.charged").value(14))
                .andExpect(jsonPath("$.summary.onRoute").value(11))
                .andExpect(jsonPath("$.summary.duplicateCharges").value(1))
                .andExpect(jsonPath("$.summary.offRoute").value(1))
                .andExpect(jsonPath("$.summary.unknownPlazas").value(1))
                .andExpect(jsonPath("$.charges[3].matchedPlaza").value("Karjeevanhally Toll Plaza"))
                .andExpect(jsonPath("$.charges[8].status").value("DUPLICATE_CHARGE"))
                .andExpect(jsonPath("$.charges[12].status").value("OFF_ROUTE"))
                .andExpect(jsonPath("$.charges[12].matchedPlaza").value("Patas Toll Plaza"))
                .andExpect(jsonPath("$.charges[12].distanceFromRouteKm", closeTo(35.9, 1.0)))
                .andExpect(jsonPath("$.charges[13].status").value("UNKNOWN_PLAZA"))
                .andExpect(jsonPath("$.notCharged[*].name", contains("Nagarhalla Toll Plaza")));
    }

    @Test
    void auditOfAPerfectStatementIsClean() throws Exception {
        mvc.perform(post("/api/v1/trips/toll-audit").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sourcePincode":"560064","destinationPincode":"411045","chargedTollPlazas":[
                         "Kadathanamale Toll Plaza","Hulikunte Toll Plaza","Chokkenahalli Toll Plaza",
                         "Karjeevanhally Toll Plaza","Guilalu Toll Plaza","Kananakatte Toll Plaza",
                         "Thimmalapura Toll Plaza","Hitnal Toll Plaza","Shahapur Toll Plaza","Vanagera Toll Plaza",
                         "Nagarhalla Toll Plaza","Bijapur Toll Plaza"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.verdict").value("CLEAN"))
                .andExpect(jsonPath("$.notCharged", hasSize(0)));
    }

    @Test
    void planAndAuditShareTheCachedRoute() throws Exception {
        mvc.perform(post("/api/v1/toll-plazas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourcePincode\":\"560064\",\"destinationPincode\":\"411045\"}"));
        mvc.perform(post("/api/v1/trips/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"560064\",\"destinationPincode\":\"411045\"}"))
                .andExpect(header().string("X-Cache", "HIT"));
        mvc.perform(post("/api/v1/trips/toll-audit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourcePincode\":\"560064\",\"destinationPincode\":\"411045\",\"chargedTollPlazas\":[]}"))
                .andExpect(jsonPath("$.summary.notCharged").value(12));

        verify(routeProvider, times(1)).route(any(), any());
    }

    @Test
    void auditRejectsAMissingChargeListWithAGenericMessage() throws Exception {
        mvc.perform(post("/api/v1/trips/toll-audit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"560064\",\"destinationPincode\":\"411045\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request"))
                .andExpect(jsonPath("$.details[0]").value("chargedTollPlazas: is required"));
    }

    @Test
    void auditKeepsTheAssignmentMessageForBadPincodes() throws Exception {
        mvc.perform(post("/api/v1/trips/toll-audit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourcePincode\":\"12\",\"destinationPincode\":\"411045\",\"chargedTollPlazas\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"));
    }

    @Test
    void mapPageIsServedAtTheRoot() throws Exception {
        mvc.perform(get("/")).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("api/v1/trips/plan")));
    }
}
