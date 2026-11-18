package com.tollroutefinder.toll;

import com.tollroutefinder.config.TollFinderProperties;
import com.tollroutefinder.toll.persistence.TollPlazaEntity;
import com.tollroutefinder.toll.persistence.TollPlazaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On startup: reads the bundled toll plaza CSV, cleans and de-duplicates it, upserts it into the database
 * (the source of truth), then builds the in-memory spatial index from the database.
 */
@Component
public class TollPlazaDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TollPlazaDataLoader.class);

    /** Same-name records further apart than this are treated as different plazas. */
    private static final double SAME_NAME_RADIUS_KM = 2.0;

    private final TollFinderProperties properties;
    private final TollPlazaCsvReader csvReader;
    private final TollPlazaRepository repository;
    private final TollPlazaIndex index;

    public TollPlazaDataLoader(TollFinderProperties properties, TollPlazaCsvReader csvReader,
                               TollPlazaRepository repository, TollPlazaIndex index) {
        this.properties = properties;
        this.csvReader = csvReader;
        this.repository = repository;
        this.index = index;
    }

    @Override
    public void run(ApplicationArguments args) {
        load();
    }

    public LoadReport load() {
        long started = System.nanoTime();
        TollPlazaCsvReader.Result csv = csvReader.read(properties.data().tollPlazaCsv());
        TollPlazaDeduplicator.Result cleaned =
                new TollPlazaDeduplicator(properties.matching().duplicateRadiusKm(), SAME_NAME_RADIUS_KM)
                        .deduplicate(csv.rows());
        int written = repository.upsertAll(cleaned.plazas());

        List<TollPlaza> stored = repository.findAll().stream().map(TollPlazaEntity::toDomain).toList();
        index.replaceAll(stored);

        LoadReport report = new LoadReport(csv.rows().size() + csv.invalidRows(), csv.invalidRows(),
                cleaned.duplicatesRemoved(), cleaned.plazas().size(), written, stored.size(),
                (System.nanoTime() - started) / 1_000_000);
        log.info("Toll plaza data loaded: {}", report);
        return report;
    }

    /**
     * Summary of a load, logged on startup.
     */
    public record LoadReport(int rowsRead, int invalidRows, int duplicatesRemoved, int uniquePlazas,
                             int rowsWritten, int indexedPlazas, long tookMs) {
    }
}
