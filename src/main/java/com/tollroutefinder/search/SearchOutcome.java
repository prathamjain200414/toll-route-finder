package com.tollroutefinder.search;

/**
 * A search result plus whether it was served from cache (exposed to clients as the {@code X-Cache} header).
 */
public record SearchOutcome(TollPlazaSearchResult result, boolean cacheHit) {
}
