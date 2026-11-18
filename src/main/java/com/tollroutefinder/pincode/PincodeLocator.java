package com.tollroutefinder.pincode;

import java.util.Optional;

/**
 * Resolves an Indian pincode to a coordinate. Implementations can be offline datasets or geocoding services.
 */
public interface PincodeLocator {

    /**
     * @param pincode a syntactically valid 6-digit pincode
     * @return the location, or empty if this locator does not know the pincode
     */
    Optional<PincodeLocation> locate(String pincode);
}
