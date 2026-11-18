package com.tollroutefinder.toll;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TollPlazaCsvReaderTest {

    private final TollPlazaCsvReader reader = new TollPlazaCsvReader();

    private static ByteArrayResource csv(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsValidRowsIncludingQuotedNamesWithCommas() {
        TollPlazaCsvReader.Result result = reader.read(csv("""
                longitude,latitude,toll_name,geo_state
                74.99684426,23.41805978,Dhamnod Toll Plaza,Madhya Pradesh
                70.34917,22.550447,"Soyal, Dhrol Toll Plaza",Gujarat
                """));

        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(1).name()).isEqualTo("Soyal, Dhrol Toll Plaza");
        assertThat(result.rows().get(1).location().latitude()).isEqualTo(22.550447);
        assertThat(result.rows().get(1).location().longitude()).isEqualTo(70.34917);
        assertThat(result.invalidRows()).isZero();
    }

    @Test
    void skipsAndCountsRowsWithBadCoordinates() {
        TollPlazaCsvReader.Result result = reader.read(csv("""
                longitude,latitude,toll_name,geo_state
                abc,23.4,Broken Toll Plaza,Madhya Pradesh
                74.9,,Missing Lat Toll Plaza,Madhya Pradesh
                74.9,123.0,Out Of Range Toll Plaza,Madhya Pradesh
                20.0,10.0,Outside India Toll Plaza,Nowhere
                74.9,23.4,Good Toll Plaza,Madhya Pradesh
                """));

        assertThat(result.rows()).extracting(RawTollPlaza::name).containsExactly("Good Toll Plaza");
        assertThat(result.invalidRows()).isEqualTo(4);
    }

    @Test
    void failsClearlyWhenRequiredColumnsAreMissing() {
        assertThatThrownBy(() -> reader.read(csv("name,lat\nfoo,1\n")))
                .isInstanceOf(TollDataException.class)
                .hasMessageContaining("longitude");
    }
}
