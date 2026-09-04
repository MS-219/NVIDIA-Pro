package com.juxin.orin.exception;

import org.springframework.http.HttpStatus;

public class EdgeDeviceApiException extends RuntimeException {

    private final HttpStatus status;

    public EdgeDeviceApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
