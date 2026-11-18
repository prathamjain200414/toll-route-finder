package com.tollroutefinder.toll;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlazaNamesTest {

    @ParameterizedTest
    @CsvSource({
            "Khed-Shivapur Toll Plaza, khedshivapur",
            "  Hitnal TOLL PLAZA , hitnal",
            "nan, ''",
            "Banglaore-Nelamangala Tollway, banglaorenelamangala"})
    void normalisesAwayNoiseWordsCaseAndPunctuation(String raw, String expected) {
        assertThat(PlazaNames.normalise(raw)).isEqualTo(expected);
    }

    @Test
    void scoresExactTypoAndContainmentMatches() {
        assertThat(PlazaNames.matchScore("hitnal", "hitnal")).hasValue(0);
        assertThat(PlazaNames.matchScore("kulumapalya", "kulumepalya")).hasValue(1);
        assertThat(PlazaNames.matchScore("banglaorenelamangala", "nelamangala")).hasValue(PlazaNames.CONTAINMENT_SCORE);
        assertThat(PlazaNames.matchScore("hitnal", "shahapur")).isEmpty();
    }

    @Test
    void shortNamesMustMatchExactly() {
        assertThat(PlazaNames.matchScore("ait", "aif")).isEmpty();
        assertThat(PlazaNames.matchScore("ait", "ait")).hasValue(0);
    }

    @Test
    void lenientMatchingAcceptsTransliterationVariants() {
        assertThat(PlazaNames.matchScore("karajeevanahalli", "karjeevanhally")).isEmpty();
        assertThat(PlazaNames.lenientMatchScore("karajeevanahalli", "karjeevanhally")).isPresent();
        assertThat(PlazaNames.lenientMatchScore("kannanakatte", "kananakatte")).isPresent();
    }

    @Test
    void lenientMatchesRankAfterStrictOnes() {
        int strict = PlazaNames.lenientMatchScore("banglaorenelamangala", "nelamangala").orElseThrow();
        int folded = PlazaNames.lenientMatchScore("karajeevanahalli", "karjeevanhally").orElseThrow();
        assertThat(folded).isGreaterThan(strict);
    }

    @Test
    void lenientMatchingStillRejectsDifferentPlaces() {
        assertThat(PlazaNames.lenientMatchScore("thimmalapura", "nagarhalla")).isEmpty();
        assertThat(PlazaNames.lenientMatchScore("sardewadi", "shahapur")).isEmpty();
    }
}
