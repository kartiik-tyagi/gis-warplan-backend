package com.gis.warplan.web;

import java.util.Map;

public record ApiError(String code, String message, String requestId, Map<String, String> fieldErrors) {

    public ApiError {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
        requestId = requestId == null ? "" : requestId;
    }
}
