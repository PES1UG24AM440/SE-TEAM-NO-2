package com.bookstore.orders;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<Map<String, Object>> handleOrderException(
            OrderException exception) {

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", exception.getCode());
        detail.put("message", exception.getMessage());
        detail.putAll(exception.getExtra());

        return ResponseEntity
                .status(exception.getStatus())
                .body(Map.of("detail", detail));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException exception) {

        List<Map<String, String>> fields =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error -> Map.of(
                                "field", error.getField(),
                                "message", error.getDefaultMessage() == null
                                        ? "Invalid value."
                                        : error.getDefaultMessage()
                        ))
                        .toList();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", "validation_error");
        detail.put(
                "message",
                "Please correct the invalid request fields."
        );
        detail.put("fields", fields);

        return ResponseEntity
                .badRequest()
                .body(Map.of("detail", detail));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableRequest(
            HttpMessageNotReadableException exception) {

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", "validation_error");
        detail.put(
                "message",
                "Please correct the invalid request fields."
        );

        return ResponseEntity
                .badRequest()
                .body(Map.of("detail", detail));
    }
}