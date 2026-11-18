package com.tollroutefinder.pincode;

import com.tollroutefinder.config.TollFinderProperties;
import com.tollroutefinder.geo.GeoPoint;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Looks pincodes up in a dataset bundled with the application (built from the India Post pincode directory by
 * {@code scripts/build_pincode_dataset.py}). ~19k pincodes, ~1 MB in memory, O(1) lookups, and no external calls
 * or rate limits.
 */
@Component
public class OfflinePincodeLocator implements PincodeLocator {

    private static final Logger log = LoggerFactory.getLogger(OfflinePincodeLocator.class);

    private final Map<String, PincodeLocation> locations;

    @Autowired
    public OfflinePincodeLocator(TollFinderProperties properties) {
        this(properties.data().pincodeCsv());
    }

    public OfflinePincodeLocator(Resource dataset) {
        this.locations = Map.copyOf(load(dataset));
        log.info("Loaded {} pincode locations from {}", locations.size(), dataset.getDescription());
    }

    @Override
    public Optional<PincodeLocation> locate(String pincode) {
        return Optional.ofNullable(locations.get(pincode));
    }

    public int size() {
        return locations.size();
    }

    private static Map<String, PincodeLocation> load(Resource dataset) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get();
        Map<String, PincodeLocation> result = new HashMap<>(32_000);
        try (Reader in = new InputStreamReader(dataset.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(in, format)) {
            for (CSVRecord record : parser) {
                try {
                    GeoPoint point = new GeoPoint(Double.parseDouble(record.get("latitude")),
                            Double.parseDouble(record.get("longitude")));
                    String pincode = record.get("pincode");
                    result.put(pincode, new PincodeLocation(pincode, point, record.get("district"), record.get("state")));
                } catch (IllegalArgumentException e) {
                    log.debug("Skipping pincode row {}: {}", record.getRecordNumber(), e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read pincode dataset " + dataset.getDescription(), e);
        }
        return result;
    }
}
