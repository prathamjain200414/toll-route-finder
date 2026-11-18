package com.tollroutefinder.trip;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EwayBillRulesTest {

    @ParameterizedTest(name = "{0} km -> {1} days")
    @CsvSource({"0, 1", "15, 1", "200, 1", "200.1, 2", "400, 2", "854, 5", "2056.2, 11"})
    void oneDayPer200KmOrPartThereof(double km, int days) {
        assertThat(EwayBillRules.validityDays(km)).isEqualTo(days);
    }
}
