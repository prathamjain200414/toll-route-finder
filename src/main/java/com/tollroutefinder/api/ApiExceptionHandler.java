package com.tollroutefinder.api;

import com.tollroutefinder.api.dto.ErrorResponse;
import com.tollroutefinder.routing.RouteNotFoundException;
import com.tollroutefinder.routing.RoutingUnavailableException;
import com.tollroutefinder.search.InvalidPincodeException;
import com.tollroutefinder.search.SamePincodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Translates every failure into the assignment's {@code {"error": "..."}} format with an accurate HTTP status.
 * Internal details (stack traces, upstream responses) are logged, never returned.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String INVALID_PINCODE = "Invalid source or destination pincode";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();
        return respond(HttpStatus.BAD_REQUEST, new ErrorResponse(INVALID_PINCODE, details));
    }

    @ExceptionHandler(InvalidPincodeException.class)
    ResponseEntity<ErrorResponse> handleUnknownPincode(InvalidPincodeException e) {
        return respond(HttpStatus.BAD_REQUEST, new ErrorResponse(INVALID_PINCODE, List.of(e.getMessage())));
    }

    @ExceptionHandler(SamePincodeException.class)
    ResponseEntity<ErrorResponse> handleSamePincode(SamePincodeException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorResponse.of("Request body is missing or is not valid JSON"));
    }

    @ExceptionHandler(RouteNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNoRoute(RouteNotFoundException e) {
        return respond(HttpStatus.UNPROCESSABLE_CONTENT, ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(RoutingUnavailableException.class)
    ResponseEntity<ErrorResponse> handleRoutingUnavailable(RoutingUnavailableException e) {
        log.warn("Routing unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(ErrorResponse.of("Routing service is temporarily unavailable, please retry shortly"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorResponse.of("Content-Type must be application/json"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ErrorResponse.of("Method " + e.getMethod() + " is not supported"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, ErrorResponse.of("Resource not found"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled error", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.of("Internal server error"));
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }
}
