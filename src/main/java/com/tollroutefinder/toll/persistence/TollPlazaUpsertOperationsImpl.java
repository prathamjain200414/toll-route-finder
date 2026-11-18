package com.tollroutefinder.toll.persistence;

import com.tollroutefinder.toll.TollPlaza;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Batched upsert using the SQL-standard {@code MERGE} statement, supported by PostgreSQL 15+ and H2.
 * The {@code WHEN MATCHED AND ...} guard skips rows whose data has not changed, so re-importing the same CSV on
 * every startup costs one round-trip per batch and zero writes.
 */
class TollPlazaUpsertOperationsImpl implements TollPlazaUpsertOperations {

    static final int BATCH_SIZE = 500;

    private static final String MERGE_SQL = """
            MERGE INTO toll_plaza t
            USING (VALUES (CAST(? AS VARCHAR(160)), CAST(? AS VARCHAR(200)), CAST(? AS VARCHAR(100)),
                           CAST(? AS DOUBLE PRECISION), CAST(? AS DOUBLE PRECISION)))
                  AS s (plaza_key, name, state, latitude, longitude)
            ON t.plaza_key = s.plaza_key
            WHEN MATCHED AND (t.name <> s.name OR t.state IS DISTINCT FROM s.state
                              OR t.latitude <> s.latitude OR t.longitude <> s.longitude) THEN
                UPDATE SET name = s.name, state = s.state, latitude = s.latitude, longitude = s.longitude,
                           updated_at = CURRENT_TIMESTAMP
            WHEN NOT MATCHED THEN
                INSERT (plaza_key, name, state, latitude, longitude, updated_at)
                VALUES (s.plaza_key, s.name, s.state, s.latitude, s.longitude, CURRENT_TIMESTAMP)
            """;

    private final JdbcTemplate jdbcTemplate;

    TollPlazaUpsertOperationsImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public int upsertAll(Collection<TollPlaza> plazas) {
        List<TollPlaza> rows = List.copyOf(plazas);
        int[][] counts = jdbcTemplate.batchUpdate(MERGE_SQL, rows, BATCH_SIZE, (ps, plaza) -> {
            ps.setString(1, plaza.key());
            ps.setString(2, plaza.name());
            ps.setString(3, plaza.state());
            ps.setDouble(4, plaza.location().latitude());
            ps.setDouble(5, plaza.location().longitude());
        });
        return Arrays.stream(counts).flatMapToInt(Arrays::stream).map(c -> Math.max(c, 0)).sum();
    }
}
