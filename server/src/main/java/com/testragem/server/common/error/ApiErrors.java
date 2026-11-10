package com.testragem.server.common.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    public static void write(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + code + "\"}");
    }

    public static class Failure extends RuntimeException {
        final HttpStatus status;
        final String code;
        public Failure(HttpStatus status, String code) {
            super(code); this.status = status; this.code = code;
        }
    }

    @ExceptionHandler(Failure.class)
    ResponseEntity<Map<String, String>> failure(Failure failure) {
        var builder = ResponseEntity.status(failure.status);
        if (failure.status == HttpStatus.TOO_MANY_REQUESTS) builder.header("Retry-After", "900");
        return builder.body(Map.of("error", failure.code));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> malformed() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
    }
}
