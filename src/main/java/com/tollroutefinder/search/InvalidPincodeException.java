package com.tollroutefinder.search;

/**
 * A pincode that is syntactically valid but does not exist (or has no known location).
 */
public class InvalidPincodeException extends RuntimeException {

    private final String pincode;

    public InvalidPincodeException(String pincode) {
        super("Pincode " + pincode + " was not found");
        this.pincode = pincode;
    }

    public String getPincode() {
        return pincode;
    }
}
