package com.gis.warplan.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC = "The request could not be completed.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException exception) {
        log.warn("Request rejected code={}", exception.getCode());
        return respond(exception.getStatus(), exception.getCode(), exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), "Invalid value."));
        log.warn("Request rejected code=VALIDATION fields={}", fields.keySet());
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION", "The request is not valid.", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> invalidConstraint(ConstraintViolationException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> fields.putIfAbsent(
                violation.getPropertyPath().toString(), "Invalid value."));
        log.warn("Request rejected code=VALIDATION fields={}", fields.keySet());
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION", "The request is not valid.", fields);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<ApiError> unreadable(Exception exception) {
        log.warn("Request rejected code=BAD_REQUEST");
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request body could not be read.", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException exception) {
        log.info("Request rejected code=METHOD_NOT_ALLOWED");
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This method is not allowed.", Map.of());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> missing(Exception exception) {
        log.info("Request rejected code=NOT_FOUND");
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested path does not exist.", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException exception) {
        log.warn("Request rejected code=FORBIDDEN");
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "This action is not allowed.", Map.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> stale(ObjectOptimisticLockingFailureException exception) {
        log.warn("Request rejected code=CONFLICT reason=OptimisticLock");
        return respond(HttpStatus.CONFLICT, "CONFLICT", "The record was changed by someone else. Reload and try again.", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> badArgument(MethodArgumentTypeMismatchException exception) {
        log.warn("Request rejected code=BAD_REQUEST");
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "A request parameter is not valid.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException exception) {
        log.warn("Request rejected code=CONFLICT reason={}", exception.getMostSpecificCause().getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data.", Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> unavailable(DataAccessException exception) {
        log.error("Database request failed", exception);
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "The service is temporarily unavailable.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Request failed", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", GENERIC, Map.of());
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String code, String message, Map<String, String> fields) {
        String requestId = ThreadContext.get(RequestIdFilter.KEY);
        return ResponseEntity.status(status).body(new ApiError(code, message, requestId, fields));
    }
}
