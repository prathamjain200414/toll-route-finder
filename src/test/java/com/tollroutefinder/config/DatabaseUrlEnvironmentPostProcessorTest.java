package com.tollroutefinder.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    private static ConfigurableEnvironment environment(Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", variables));
        return environment;
    }

    private ConfigurableEnvironment process(Map<String, Object> variables) {
        ConfigurableEnvironment environment = environment(variables);
        processor.postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    @Test
    void convertsNeonPooledUrlIntoJdbcDatasourceSettings() {
        ConfigurableEnvironment env = process(Map.of("DATABASE_URL",
                "postgresql://app_user:s3cret@ep-cool-name-123-pooler.ap-southeast-1.aws.neon.tech/neondb"
                        + "?sslmode=require&channel_binding=require"));

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo(
                "jdbc:postgresql://ep-cool-name-123-pooler.ap-southeast-1.aws.neon.tech:5432/neondb"
                        + "?sslmode=require&channelBinding=require");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("app_user");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("s3cret");
    }

    @Test
    void usesTheDirectUrlForFlywayMigrations() {
        ConfigurableEnvironment env = process(Map.of(
                "DATABASE_URL", "postgresql://u:p@ep-x-pooler.neon.tech/db?sslmode=require",
                "DATABASE_URL_UNPOOLED", "postgresql://u:p@ep-x.neon.tech/db?sslmode=require"));

        assertThat(env.getProperty("spring.flyway.url")).isEqualTo("jdbc:postgresql://ep-x.neon.tech:5432/db?sslmode=require");
        assertThat(env.getProperty("spring.flyway.user")).isEqualTo("u");
        assertThat(env.getProperty("spring.flyway.password")).isEqualTo("p");
        assertThat(env.getProperty("spring.datasource.url")).contains("ep-x-pooler");
    }

    @Test
    void decodesPercentEncodedCredentialsAndKeepsExplicitPort() {
        ConfigurableEnvironment env = process(Map.of("DATABASE_URL", "postgres://us%40er:p%40ss%3Aw0rd@db.example.com:6543/app"));

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.example.com:6543/app");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("us@er");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p@ss:w0rd");
    }

    @Test
    void ignoresWhitespacePastedAroundTheUrl() {
        ConfigurableEnvironment env = process(Map.of("DATABASE_URL", "  postgresql://u:p@host.neon.tech/db?sslmode=require\n"));

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://host.neon.tech:5432/db?sslmode=require");
    }

    @Test
    void leavesJdbcUrlsAlone() {
        ConfigurableEnvironment env = process(Map.of("DATABASE_URL", "jdbc:postgresql://localhost:5432/tolls"));

        assertThat(env.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void doesNothingWithoutDatabaseUrl() {
        ConfigurableEnvironment env = process(Map.of());

        assertThat(env.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void rejectsMalformedUrlWithoutEchoingCredentials() {
        assertThatThrownBy(() -> process(Map.of("DATABASE_URL", "postgresql://user:topsecret@/")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_URL")
                .hasMessageNotContaining("topsecret");
    }
}
