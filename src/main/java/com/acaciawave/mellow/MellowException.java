package com.acaciawave.mellow;

/**
 * Unchecked error type used throughout Mellow. Command handlers, the SSH layer
 * and config code all surface failures as {@code MellowException} so handlers
 * can stay free of checked-exception noise (the Java port of Go's {@code error}).
 */
public class MellowException extends RuntimeException {

    public MellowException(String message) {
        super(message);
    }

    public MellowException(String message, Throwable cause) {
        super(message, cause);
    }
}