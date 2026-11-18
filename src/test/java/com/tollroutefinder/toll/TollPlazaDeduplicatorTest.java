package com.tollroutefinder.toll;

import com.tollroutefinder.geo.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TollPlazaDeduplicatorTest {

    private final TollPlazaDeduplicator deduplicator = new TollPlazaDeduplicator(0.3, 2.0);

    private static RawTollPlaza raw(String name, double lat, double lon) {
        return new RawTollPlaza(name, new GeoPoint(lat, lon), "Maharashtra");
    }

    @Test
    void collapsesExactDuplicates() {
        List<TollPlaza> result = deduplicator.deduplicate(List.of(
                raw("Anewadi Toll Plaza", 17.8103317, 73.9710349),
                raw("Anewadi Toll Plaza", 17.8103317, 73.9710349),
                raw("Anewadi Toll Plaza", 17.8103317, 73.9710349))).plazas();

        assertThat(result).singleElement().extracting(TollPlaza::name).isEqualTo("Anewadi Toll Plaza");
    }

    @Test
    void mergesSpellingVariantsOfTheSamePlazaThatAreClose() {
        List<TollPlaza> result = deduplicator.deduplicate(List.of(
                raw("Khed-Shivapur Toll Plaza", 18.3313818, 73.8524647),
                raw("Khedshivapur Toll Plaza", 18.3315641, 73.8512103))).plazas();

        assertThat(result).hasSize(1);
    }

    @Test
    void mergesSameNameWithinTheNameRadiusEvenIfCoordinatesDrift() {
        // ~1.1 km apart, same plaza recorded twice
        List<TollPlaza> result = deduplicator.deduplicate(List.of(
                raw("Hiwargaon Pavasa Toll Plaza", 19.5143978, 74.200879),
                raw("Hiwargaon Pavasa Toll Plaza", 19.5152066, 74.2102))).plazas();

        assertThat(result).hasSize(1);
    }

    @Test
    void mergesNearbyPlazasWhoseNamesDifferByATypo() {
        // Real rows from the dataset, ~0.45 km apart
        List<TollPlaza> result = deduplicator.deduplicate(List.of(
                raw("Hiwargaon Pavasa Toll Plaza", 19.5143978, 74.200879),
                raw("Hiwargaon Pawasa Toll Plaza", 19.5152066, 74.1964302))).plazas();

        assertThat(result).singleElement().extracting(TollPlaza::name).isEqualTo("Hiwargaon Pavasa Toll Plaza");
    }

    @Test
    void keepsDistinctPlazasThatShareANameButAreFarApart() {
        List<TollPlaza> result = deduplicator.deduplicate(List.of(
                raw("Dhamnod Toll Plaza", 23.41805978, 74.99684426),
                raw("Dhamnod Toll Plaza", 22.2162677, 75.5132989))).plazas();

        assertThat(result).hasSize(2);
    }

    @Test
    void keepsDifferentlyNamedPlazasThatAreModeratelyClose() {
        // ~1 km apart, genuinely different plazas
        List<TollPlaza> result = deduplicator.deduplicate(List.of(
                raw("Kurana Toll Plaza", 28.6280941, 77.7848776),
                raw("Other Toll Plaza", 28.6370941, 77.7848776))).plazas();

        assertThat(result).hasSize(2);
    }

    @Test
    void unnamedRecordAdoptsNameOfNearbyNamedPlaza() {
        List<TollPlaza> result = deduplicator.deduplicate(List.of(
                raw("nan", 22.1605721, 74.7744097),
                raw("Susari Toll Plaza", 22.1605721, 74.7744097))).plazas();

        assertThat(result).singleElement().extracting(TollPlaza::name).isEqualTo("Susari Toll Plaza");
    }

    @Test
    void isolatedUnnamedRecordIsKeptWithPlaceholderName() {
        List<TollPlaza> result = deduplicator.deduplicate(List.of(raw("  ", 20.0, 80.0))).plazas();

        assertThat(result).singleElement().extracting(TollPlaza::name).isEqualTo(TollPlaza.UNNAMED);
    }

    @Test
    void reportsHowManyRecordsWereMerged() {
        TollPlazaDeduplicator.Result result = deduplicator.deduplicate(List.of(
                raw("A Toll Plaza", 20.0, 80.0),
                raw("A Toll Plaza", 20.0, 80.0),
                raw("B Toll Plaza", 21.0, 80.0)));

        assertThat(result.plazas()).hasSize(2);
        assertThat(result.duplicatesRemoved()).isEqualTo(1);
    }

    @Test
    void producesAStableKeyPerPlaza() {
        TollPlaza a = deduplicator.deduplicate(List.of(raw("Anewadi Toll Plaza", 17.8103317, 73.9710349))).plazas().getFirst();
        TollPlaza b = deduplicator.deduplicate(List.of(raw("Anewadi Toll Plaza", 17.8103317, 73.9710349))).plazas().getFirst();

        assertThat(a.key()).isEqualTo(b.key()).isEqualTo("anewadi@17.8103,73.9710");
    }

    @Test
    void normalisesNamesForComparison() {
        assertThat(TollPlazaDeduplicator.normalise("Khed-Shivapur Toll Plaza")).isEqualTo("khedshivapur");
        assertThat(TollPlazaDeduplicator.normalise("Dahisar Toll Booth")).isEqualTo("dahisar");
        assertThat(TollPlazaDeduplicator.normalise("Bamanbore Toll Gate")).isEqualTo("bamanbore");
        assertThat(TollPlazaDeduplicator.normalise("nan")).isEmpty();
    }
}
