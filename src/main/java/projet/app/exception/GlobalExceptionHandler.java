package projet.app.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FormulaValidationException.class)
        public ResponseEntity<?> handleFormulaValidation(
            FormulaValidationException ex,
            HttpServletRequest request
    ) {
                ResponseEntity<?> sse = sseErrorIfRequested(request, "Formula validation failed");
                if (sse != null) return sse;
        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Formula validation failed")
                .details(ex.getErrors())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(InvalidSqlException.class)
        public ResponseEntity<?> handleInvalidSql(
            InvalidSqlException ex,
            HttpServletRequest request
    ) {
                ResponseEntity<?> sse = sseErrorIfRequested(request, ex.getMessage());
                if (sse != null) return sse;
        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("INVALID_SQL")
                .message(ex.getMessage())
                .details(ex.getDetails())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(ParameterConfigNotFoundException.class)
        public ResponseEntity<?> handleConfigNotFound(
            ParameterConfigNotFoundException ex,
            HttpServletRequest request
    ) {
                ResponseEntity<?> sse = sseErrorIfRequested(request, ex.getMessage());
                if (sse != null) return sse;
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .details(List.of())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(RatiosConfigNotFoundException.class)
        public ResponseEntity<?> handleRatiosConfigNotFound(
            RatiosConfigNotFoundException ex,
            HttpServletRequest request
    ) {
                ResponseEntity<?> sse = sseErrorIfRequested(request, ex.getMessage());
                if (sse != null) return sse;
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .details(List.of())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(FamilleRatiosNotFoundException.class)
        public ResponseEntity<?> handleFamilleRatiosNotFound(
            FamilleRatiosNotFoundException ex,
            HttpServletRequest request
    ) {
                ResponseEntity<?> sse = sseErrorIfRequested(request, ex.getMessage());
                if (sse != null) return sse;
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .details(List.of())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(CategorieRatiosNotFoundException.class)
        public ResponseEntity<?> handleCategorieRatiosNotFound(
            CategorieRatiosNotFoundException ex,
            HttpServletRequest request
    ) {
                ResponseEntity<?> sse = sseErrorIfRequested(request, ex.getMessage());
                if (sse != null) return sse;
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .details(List.of())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(StressTestException.class)
        public ResponseEntity<?> handleStressTest(
            StressTestException ex,
            HttpServletRequest request
    ) {
                ResponseEntity<?> sse = sseErrorIfRequested(request, ex.getMessage());
                if (sse != null) return sse;
        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(ex.getErrorCode())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<?> handleBeanValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<String> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }

                String message = details.isEmpty()
                                ? "Request validation failed"
                                : "Request validation failed: " + String.join(", ", details);
                ResponseEntity<?> sse = sseErrorIfRequested(request, message);
                if (sse != null) return sse;

        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Request validation failed")
                .details(details)
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<?> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
                ResponseEntity<?> sse = sseErrorIfRequested(request, ex.getMessage());
                if (sse != null) return sse;
        return ResponseEntity.badRequest().body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .details(List.of())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(Exception.class)
        public ResponseEntity<?> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
                String message = ex.getMessage();
                if (message == null || message.isBlank()) {
                        message = "Unexpected internal server error";
                }
                ResponseEntity<?> sse = sseErrorIfRequested(request, message);
                if (sse != null) return sse;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Unexpected internal server error")
                .details(List.of(ex.getMessage() == null ? "No error message provided" : ex.getMessage()))
                .path(request.getRequestURI())
                .build());
    }

        private ResponseEntity<?> sseErrorIfRequested(HttpServletRequest request, String message) {
                String accept = request.getHeader("Accept");
                if (accept != null && accept.toLowerCase(Locale.ROOT)
                        .contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
                        String payload = "event: error\ndata: {\"message\":\"" +
                                        escapeJson(message) + "\"}\n\n";
                        return ResponseEntity.ok()
                                        .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(payload.getBytes(StandardCharsets.UTF_8));
                }
                return null;
        }

        private static String escapeJson(String value) {
                if (value == null || value.isBlank()) return "Unknown error";
                return value.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r");
        }
}
