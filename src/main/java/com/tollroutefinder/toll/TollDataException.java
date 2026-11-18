package com.tollroutefinder.toll;

/**
 * Raised when the toll plaza reference data cannot be read. This is a startup-time failure.
 */
public class TollDataException extends RuntimeException {

    public TollDataException(String message) {
        super(message);
    }

    public TollDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
