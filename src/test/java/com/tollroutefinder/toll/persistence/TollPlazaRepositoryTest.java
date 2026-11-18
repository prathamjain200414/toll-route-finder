package com.tollroutefinder.toll.persistence;

import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.toll.TollPlaza;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TollPlazaRepositoryTest {

    @Autowired
    private TollPlazaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static TollPlaza plaza(String key, String name, double lat, double lon) {
        return new TollPlaza(key, name, new GeoPoint(lat, lon), "Karnataka");
    }

    @Test
    void insertsNewPlazas() {
        int written = repository.upsertAll(List.of(
                plaza("a@1", "A Toll Plaza", 12.0, 77.0),
                plaza("b@1", "B Toll Plaza", 13.0, 77.0)));

        assertThat(written).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByPlazaKey("a@1")).get()
                .extracting(TollPlazaEntity::getName, TollPlazaEntity::getLatitude)
                .containsExactly("A Toll Plaza", 12.0);
    }

    @Test
    void reimportingIdenticalDataWritesNothing() {
        List<TollPlaza> plazas = List.of(plaza("a@1", "A Toll Plaza", 12.0, 77.0));
        repository.upsertAll(plazas);

        int written = repository.upsertAll(plazas);

        assertThat(written).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void updatesChangedPlazasInPlace() {
        repository.upsertAll(List.of(plaza("a@1", "A Toll Plaza", 12.0, 77.0)));
        Long id = repository.findByPlazaKey("a@1").orElseThrow().getId();

        int written = repository.upsertAll(List.of(plaza("a@1", "A Renamed Toll Plaza", 12.0, 77.0)));

        assertThat(written).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        entityManager.clear(); // the JDBC MERGE bypasses Hibernate's first-level cache
        TollPlazaEntity updated = repository.findByPlazaKey("a@1").orElseThrow();
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getName()).isEqualTo("A Renamed Toll Plaza");
    }

    @Test
    void handlesMoreRowsThanOneBatch() {
        List<TollPlaza> many = IntStream.range(0, TollPlazaUpsertOperationsImpl.BATCH_SIZE + 37)
                .mapToObj(i -> plaza("p@" + i, "Plaza " + i, 10 + i * 0.001, 77.0))
                .toList();

        assertThat(repository.upsertAll(many)).isEqualTo(many.size());
        assertThat(repository.count()).isEqualTo(many.size());
    }

    @Test
    void mapsEntityToDomain() {
        repository.upsertAll(List.of(plaza("a@1", "A Toll Plaza", 12.5, 77.25)));

        TollPlaza domain = repository.findByPlazaKey("a@1").orElseThrow().toDomain();

        assertThat(domain).isEqualTo(plaza("a@1", "A Toll Plaza", 12.5, 77.25));
    }
}
