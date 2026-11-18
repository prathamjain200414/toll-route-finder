package com.tollroutefinder.search;

/**
 * Source and destination pincodes are identical, so there is no route to search.
 */
public class SamePincodeException extends RuntimeException {

    public SamePincodeException() {
        super("Source and destination pincodes cannot be the same");
    }
}
