package com.gis.warplan.service.geoserver;

public class GeoServerException extends RuntimeException {

    private final int status;
    private final boolean retryable;
    private final boolean unauthorized;

    public GeoServerException(String message, int status) {
        super(message);
        this.status = status;
        this.retryable = status >= 500 || status == 408 || status == 429;
        this.unauthorized = status == 401 || status == 403;
    }

    public int getStatus() {
        return status;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isUnauthorized() {
        return unauthorized;
    }
}
