package com.tollroutefinder.toll;

import com.tollroutefinder.geo.GeoPoint;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the toll plaza CSV ({@code longitude,latitude,toll_name,geo_state}). Rows with unusable coordinates are
 * skipped and counted rather than failing the whole load.
 */
@Component
public class TollPlazaCsvReader {

    private static final Logger log = LoggerFactory.getLogger(TollPlazaCsvReader.class);

    static final String LONGITUDE = "longitude";
    static final String LATITUDE = "latitude";
    static final String NAME = "toll_name";
    static final String STATE = "geo_state";

    // Generous bounding box around India, used to reject obviously wrong coordinates.
    private static final double MIN_LAT = 6.0, MAX_LAT = 37.5, MIN_LON = 68.0, MAX_LON = 97.5;

    public Result read(Resource resource) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .get();
        try (Reader in = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(in, format)) {
            requireColumns(parser, LONGITUDE, LATITUDE, NAME, STATE);
            List<RawTollPlaza> rows = new ArrayList<>();
            int invalid = 0;
            for (CSVRecord record : parser) {
                RawTollPlaza row = toRow(record);
                if (row == null) {
                    invalid++;
                } else {
                    rows.add(row);
                }
            }
            return new Result(rows, invalid);
        } catch (IOException e) {
            throw new TollDataException("Could not read toll plaza data from " + resource.getDescription(), e);
        }
    }

    private static void requireColumns(CSVParser parser, String... columns) {
        for (String column : columns) {
            if (!parser.getHeaderMap().containsKey(column)) {
                throw new TollDataException("Toll plaza CSV is missing required column '" + column + "'");
            }
        }
    }

    private static RawTollPlaza toRow(CSVRecord record) {
        try {
            double lat = Double.parseDouble(record.get(LATITUDE));
            double lon = Double.parseDouble(record.get(LONGITUDE));
            if (lat < MIN_LAT || lat > MAX_LAT || lon < MIN_LON || lon > MAX_LON) {
                log.debug("Skipping toll row {} with coordinates outside India: {},{}", record.getRecordNumber(), lat, lon);
                return null;
            }
            return new RawTollPlaza(record.get(NAME), new GeoPoint(lat, lon), record.get(STATE));
        } catch (IllegalArgumentException e) {
            // NumberFormatException is an IllegalArgumentException, as is an out-of-range GeoPoint
            log.debug("Skipping malformed toll row {}: {}", record.getRecordNumber(), e.getMessage());
            return null;
        }
    }

    public record Result(List<RawTollPlaza> rows, int invalidRows) {
    }
}
