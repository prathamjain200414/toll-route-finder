package com.tollroutefinder.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI tollRouteFinderOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Toll Route Finder API")
                .version("1.0")
                .description("""
                        Finds the toll plazas on the driving route between two Indian pincodes.

                        **Try it:** `{"sourcePincode": "560064", "destinationPincode": "411045"}` (Bengaluru to Pune).
                        Send it twice: the second response carries `X-Cache: HIT`.

                        * Pincodes resolve offline from the India Post directory (19k+ pincodes).
                        * Routes come from OSRM (or the OpenRouteService truck profile when configured).
                        * A plaza is on the route if it lies within 1 km of the road; `distanceFromSource` is the \
                        driving distance along the route."""));
    }
}
