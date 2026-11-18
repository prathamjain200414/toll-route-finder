package com.tollroutefinder.pincode;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class OfflinePincodeLocatorTest {

    private static OfflinePincodeLocator fromCsv(String content) {
        return new OfflinePincodeLocator(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void locatesAKnownPincode() {
        OfflinePincodeLocator locator = fromCsv("""
                pincode,latitude,longitude,district,state
                560064,13.114503,77.574411,Bengaluru Urban,Karnataka
                """);

        PincodeLocation location = locator.locate("560064").orElseThrow();

        assertThat(location.pincode()).isEqualTo("560064");
        assertThat(location.point().latitude()).isEqualTo(13.114503);
        assertThat(location.point().longitude()).isEqualTo(77.574411);
        assertThat(location.district()).isEqualTo("Bengaluru Urban");
        assertThat(location.state()).isEqualTo("Karnataka");
    }

    @Test
    void returnsEmptyForUnknownPincode() {
        OfflinePincodeLocator locator = fromCsv("pincode,latitude,longitude,district,state\n");

        assertThat(locator.locate("999999")).isEmpty();
    }

    @Test
    void skipsRowsWithBadCoordinates() {
        OfflinePincodeLocator locator = fromCsv("""
                pincode,latitude,longitude,district,state
                111111,abc,77.0,X,Y
                222222,12.0,77.0,X,Y
                """);

        assertThat(locator.locate("111111")).isEmpty();
        assertThat(locator.locate("222222")).isPresent();
        assertThat(locator.size()).isEqualTo(1);
    }

    @Test
    void failsFastWhenDatasetIsMissing() {
        assertThatThrownBy(() -> new OfflinePincodeLocator(new ClassPathResource("data/does-not-exist.csv")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bundledDatasetCoversTheAssignmentTestPincodes() {
        OfflinePincodeLocator locator = new OfflinePincodeLocator(new ClassPathResource("data/pincodes.csv"));

        assertThat(locator.size()).isGreaterThan(19_000);
        assertThat(locator.locate("110001")).get()
                .satisfies(l -> assertThat(l.point().latitude()).isCloseTo(28.62, within(0.05)));
        assertThat(locator.locate("560001")).isPresent();
        assertThat(locator.locate("560064")).isPresent();
        assertThat(locator.locate("411045")).isPresent();
    }
}
