package com.gis.warplan.web;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super("BAD_REQUEST", HttpStatus.BAD_REQUEST, message);
    }

    public BadRequestException(String message, Map<String, String> fieldErrors) {
        super("BAD_REQUEST", HttpStatus.BAD_REQUEST, message, fieldErrors);
    }
}
