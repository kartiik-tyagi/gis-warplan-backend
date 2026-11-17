package com.drdo.jayant.web;

import org.springframework.http.HttpStatus;

public class UnavailableException extends ApiException {

    public UnavailableException(String message) {
        super("UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
