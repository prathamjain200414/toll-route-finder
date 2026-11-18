package com.tollroutefinder.api;

import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.routing.RouteNotFoundException;
import com.tollroutefinder.routing.RoutingUnavailableException;
import com.tollroutefinder.search.InvalidPincodeException;
import com.tollroutefinder.search.SamePincodeException;
import com.tollroutefinder.search.SearchOutcome;
import com.tollroutefinder.search.TollPlazaSearchResult;
import com.tollroutefinder.search.TollPlazaSearchService;
import com.tollroutefinder.toll.TollOnRoute;
import com.tollroutefinder.toll.TollPlaza;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TollPlazaController.class)
@Import({TollPlazaResponseMapper.class, ApiExceptionHandler.class})
class TollPlazaControllerTest {

    private static final String URL = "/api/v1/toll-plazas";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TollPlazaSearchService service;

    private static String body(String source, String destination) {
        return "{\"sourcePincode\":\"%s\",\"destinationPincode\":\"%s\"}".formatted(source, destination);
    }

    private static SearchOutcome outcome(boolean cacheHit, TollOnRoute... tolls) {
        return new SearchOutcome(new TollPlazaSearchResult("110001", "560001", 2100.04, "OSRM", List.of(tolls)), cacheHit);
    }

    private static TollOnRoute toll(String name, double lat, double lon, double fromSource) {
        return new TollOnRoute(new TollPlaza(name, name, new GeoPoint(lat, lon), "X"), fromSource, 0.1);
    }

    @Test
    void returnsRouteAndTollPlazasInTheSpecifiedShape() throws Exception {
        when(service.search("110001", "560001")).thenReturn(outcome(false,
                toll("Toll Plaza 1", 28.7041, 77.1025, 200.04),
                toll("Toll Plaza 2", 19.076, 72.8777, 1399.96)));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("110001", "560001")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache", "MISS"))
                .andExpect(header().string("X-Route-Provider", "OSRM"))
                .andExpect(content().json("""
                        {"route":{"sourcePincode":"110001","destinationPincode":"560001","distanceInKm":2100.0},
                         "tollPlazas":[
                           {"name":"Toll Plaza 1","latitude":28.7041,"longitude":77.1025,"distanceFromSource":200.0},
                           {"name":"Toll Plaza 2","latitude":19.076,"longitude":72.8777,"distanceFromSource":1400.0}]}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void routeWithoutTollsReturnsEmptyArray() throws Exception {
        when(service.search("110001", "560001")).thenReturn(outcome(false));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("110001", "560001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tollPlazas", hasSize(0)))
                .andExpect(jsonPath("$.route.distanceInKm").value(2100.0));
    }

    @Test
    void cachedResultIsFlaggedInHeader() throws Exception {
        when(service.search("110001", "560001")).thenReturn(outcome(true));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("110001", "560001")))
                .andExpect(header().string("X-Cache", "HIT"));
    }

    @Test
    void malformedPincodeIsRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("11000", "560001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"))
                .andExpect(jsonPath("$.details[0]").value("sourcePincode: must be a 6-digit Indian pincode starting with 1-8"));
        verifyNoInteractions(service);
    }

    @Test
    void pincodeStartingWithZeroIsRejected() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("110001", "012345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.startsWith("destinationPincode")));
    }

    @Test
    void armyPostalServicePincodeIsRejected() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("999999", "560001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"));
    }

    @Test
    void lettersInPincodeAreRejected() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("11000A", "560001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"));
    }

    @Test
    void missingFieldsAreReported() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"))
                .andExpect(jsonPath("$.details", hasSize(2)));
    }

    @Test
    void unknownPincodeReturnsTheSpecifiedError() throws Exception {
        when(service.search("100000", "560001")).thenThrow(new InvalidPincodeException("100000"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("100000", "560001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"))
                .andExpect(jsonPath("$.details[0]").value("Pincode 100000 was not found"));
    }

    @Test
    void identicalPincodesReturnTheSpecifiedError() throws Exception {
        when(service.search("110001", "110001")).thenThrow(new SamePincodeException());

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("110001", "110001")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Source and destination pincodes cannot be the same\"}",
                        org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void noDrivableRouteIsUnprocessable() throws Exception {
        when(service.search(anyString(), anyString())).thenThrow(new RouteNotFoundException("No drivable route exists"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("110001", "744101")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("No drivable route exists"));
    }

    @Test
    void routingOutageIsServiceUnavailableWithRetryAfter() throws Exception {
        when(service.search(anyString(), anyString())).thenThrow(new RoutingUnavailableException("upstream 502"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("110001", "560001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.error").value("Routing service is temporarily unavailable, please retry shortly"));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() throws Exception {
        when(service.search(anyString(), anyString())).thenThrow(new IllegalStateException("db password is hunter2"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("110001", "560001")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"error\":\"Internal server error\"}",
                        org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void invalidJsonIsBadRequest() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"sourcePincode\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request body is missing or is not valid JSON"));
    }

    @Test
    void missingBodyIsBadRequest() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonJsonContentTypeIsUnsupported() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("110001,560001"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void getIsNotAllowed() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Method GET is not supported"));
    }
}
