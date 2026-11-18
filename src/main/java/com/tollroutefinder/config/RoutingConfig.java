package com.tollroutefinder.config;

import com.tollroutefinder.routing.FallbackRouteProvider;
import com.tollroutefinder.routing.OrsRouteProvider;
import com.tollroutefinder.routing.OsrmRouteProvider;
import com.tollroutefinder.routing.RetryingRouteProvider;
import com.tollroutefinder.routing.RouteProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the routing chain:
 * <pre>
 *   Fallback( Retry(OpenRouteService truck profile) , Retry(OSRM) )   when ORS_API_KEY is set
 *   Retry(OSRM)                                                       otherwise
 * </pre>
 * Adding another engine means writing one adapter and adding it to the list here.
 */
@Configuration(proxyBeanMethods = false)
public class RoutingConfig {

    private static final Logger log = LoggerFactory.getLogger(RoutingConfig.class);

    @Bean
    RouteProvider routeProvider(TollFinderProperties properties) {
        TollFinderProperties.Routing routing = properties.routing();
        List<RouteProvider> chain = new ArrayList<>();

        TollFinderProperties.Ors ors = routing.ors();
        if (ors.enabled()) {
            RestClient client = restClient(ors.baseUrl(), ors.connectTimeout(), ors.readTimeout());
            chain.add(retrying(new OrsRouteProvider(client, ors.profile(), ors.apiKey()), routing.retry()));
        }

        TollFinderProperties.Osrm osrm = routing.osrm();
        RestClient osrmClient = restClient(osrm.baseUrl(), osrm.connectTimeout(), osrm.readTimeout());
        chain.add(retrying(new OsrmRouteProvider(osrmClient, osrm.profile()), routing.retry()));

        RouteProvider provider = chain.size() == 1 ? chain.getFirst() : new FallbackRouteProvider(chain);
        log.info("Routing chain: {}", provider.name());
        return provider;
    }

    private static RouteProvider retrying(RouteProvider provider, TollFinderProperties.Retry retry) {
        return new RetryingRouteProvider(provider, retry.maxAttempts(), retry.backoff());
    }

    private static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "toll-route-finder/1.0")
                // Pin gzip: with "deflate" negotiated, OSRM's deflate stream fails to inflate ("incorrect header check").
                .defaultHeader("Accept-Encoding", "gzip")
                .build();
    }
}
