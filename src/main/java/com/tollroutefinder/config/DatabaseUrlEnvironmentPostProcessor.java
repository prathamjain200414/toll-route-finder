package com.tollroutefinder.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Lets the app run on the {@code postgresql://user:pass@host/db} URLs that Neon (and Render, Heroku, ...) hand out,
 * which JDBC does not accept.
 * <ul>
 *   <li>{@code DATABASE_URL} (Neon's pooled endpoint) becomes {@code spring.datasource.*} for application traffic.</li>
 *   <li>{@code DATABASE_URL_UNPOOLED} (direct endpoint), if present, becomes {@code spring.flyway.*}: schema
 *       migrations should not go through the connection pooler.</li>
 * </ul>
 * A {@code DATABASE_URL} that is already a {@code jdbc:} URL is left to the normal property binding.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "databaseUrlConversion";

    private static final int DEFAULT_PORT = 5432;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();
        convert(environment.getProperty("DATABASE_URL"), "DATABASE_URL").ifPresent(c -> {
            properties.put("spring.datasource.url", c.jdbcUrl());
            properties.put("spring.datasource.username", c.username());
            properties.put("spring.datasource.password", c.password());
        });
        convert(environment.getProperty("DATABASE_URL_UNPOOLED"), "DATABASE_URL_UNPOOLED").ifPresent(c -> {
            properties.put("spring.flyway.url", c.jdbcUrl());
            properties.put("spring.flyway.user", c.username());
            properties.put("spring.flyway.password", c.password());
        });
        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static java.util.Optional<Connection> convert(String url, String variable) {
        if (url == null || !(url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
            return java.util.Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw invalid(variable);
        }
        String path = uri.getRawPath();
        if (uri.getHost() == null || path == null || path.length() <= 1) {
            throw invalid(variable);
        }
        String username = "";
        String password = "";
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            username = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
            password = colon < 0 ? "" : decode(userInfo.substring(colon + 1));
        }
        int port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_PORT;
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost()).append(':').append(port).append(path);
        String query = uri.getRawQuery();
        if (query != null && !query.isBlank()) {
            // libpq spells it channel_binding; pgjdbc calls the same option channelBinding.
            jdbc.append('?').append(query.replace("channel_binding=", "channelBinding="));
        }
        return java.util.Optional.of(new Connection(jdbc.toString(), username, password));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static IllegalStateException invalid(String variable) {
        // Never include the URL itself: it contains the password.
        return new IllegalStateException(variable + " is not a valid postgresql://user:password@host/database URL");
    }

    private record Connection(String jdbcUrl, String username, String password) {
    }
}
