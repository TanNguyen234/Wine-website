package com.strongwine.strongwine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestControllerAdvice(basePackages = "com.strongwine.strongwine.controller.api")
public class ApiExceptionHandler {

    private static final Pattern FILESYSTEM_PATH_LEAK_PATTERN = Pattern.compile(
            "(?i)(?:java\\.nio\\.file\\.[A-Za-z]+Exception|[a-z]:\\\\[^\\s]+|(?:/tmp/|/var/folders/|\\\\Temp\\\\)[^\\s]*)"
    );

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            RuntimeException ex,
            jakarta.servlet.http.HttpServletResponse response) {
        if (isSseOrCommitted(response)) {
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(sanitizeMessage(ex.getMessage())));
    }

    @ExceptionHandler({FileSystemException.class, UncheckedIOException.class})
    public ResponseEntity<Map<String, String>> handleFileSystemError(
            Exception ex,
            jakarta.servlet.http.HttpServletResponse response) {
        if (isSseOrCommitted(response)) {
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("Đã xảy ra lỗi xử lý tệp dữ liệu tạm trên máy chủ. Vui lòng thử lại."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "Invalid request";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(sanitizeMessage(message)));
    }

    private boolean isSseOrCommitted(jakarta.servlet.http.HttpServletResponse response) {
        if (response == null) {
            return false;
        }
        String contentType = response.getContentType();
        return response.isCommitted() || (contentType != null && contentType.contains("text/event-stream"));
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Yêu cầu không hợp lệ";
        }
        if (FILESYSTEM_PATH_LEAK_PATTERN.matcher(message).find()) {
            return "Đã xảy ra lỗi xử lý tệp dữ liệu trên máy chủ. Vui lòng thử lại.";
        }
        return message;
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error", message);
        return body;
    }
}
