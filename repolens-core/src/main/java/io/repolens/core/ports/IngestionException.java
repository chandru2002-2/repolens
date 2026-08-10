package io.repolens.core.ports;

/**
 * Thrown when ingestion fails due to invalid input, unsupported mode, or safety limits.
 */
public class IngestionException extends RuntimeException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
