package com.tollroutefinder.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Kept separate from the main application class so that test slices (e.g. {@code @DataJpaTest}) don't require a
 * CacheManager. Cache sizing is configured in application.yml (Caffeine spec).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    public static final String TOLL_SEARCH_CACHE = "tollSearch";
}
