package projet.app.ai.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import projet.app.ai.tools.BackendApiClient.BackendCallException;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps AI-module exceptions into structured {@link AiErrorResponse} bodies.
 * Scoped to {@code projet.app.ai} packages so it does NOT shadow the existing
 * application's exception handling for unrelated controllers.
 */
@Slf4j
@Order(0)
@RestControllerAdvice(basePackages = "projet.app.ai")
public class AiGlobalExceptionHandler {

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<AiErrorResponse> handleToolFailure(ToolExecutionException ex,
                                                             HttpServletRequest req) {
        String cid = correlationId(req);
        log.warn("[{}] Tool '{}' failed: {}", cid, ex.getToolName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(AiErrorResponse.builder()
                .code("TOOL_EXECUTION_FAILED")
                .message("Tool '" + ex.getToolName() + "' failed.")
                .detail(ex.getMessage())
                .correlationId(cid)
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(BackendCallException.class)
    public ResponseEntity<AiErrorResponse> handleBackendCall(BackendCallException ex,
                                                             HttpServletRequest req) {
        String cid = correlationId(req);
        log.warn("[{}] Backend call failed: {}", cid, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(AiErrorResponse.builder()
                .code("BACKEND_CALL_FAILED")
                .message("Backend loopback call failed.")
                .detail(ex.getMessage())
                .correlationId(cid)
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<AiErrorResponse> handleUploadSize(MaxUploadSizeExceededException ex,
                                                            HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(AiErrorResponse.builder()
                .code("PAYLOAD_TOO_LARGE")
                .message("Uploaded file is too large.")
                .detail(ex.getMessage())
                .correlationId(correlationId(req))
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                            HttpServletRequest req) {
        String details = ex.getBindingResult().getAllErrors().stream()
                .map(err -> err.getDefaultMessage() == null ? err.toString() : err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request.");
        return ResponseEntity.badRequest().body(AiErrorResponse.builder()
                .code("VALIDATION_FAILED")
                .message("Request validation failed.")
                .detail(details)
                .correlationId(correlationId(req))
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AiErrorResponse> handleIllegalArg(IllegalArgumentException ex,
                                                            HttpServletRequest req) {
        return ResponseEntity.badRequest().body(AiErrorResponse.builder()
                .code("BAD_REQUEST")
                .message(ex.getMessage())
                .correlationId(correlationId(req))
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AiErrorResponse> handleAny(Exception ex, HttpServletRequest req) {
        String cid = correlationId(req);
        log.error("[{}] Unhandled AI module error", cid, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AiErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("Unexpected server error.")
                .detail(ex.getMessage())
                .correlationId(cid)
                .timestamp(Instant.now())
                .build());
    }

    private static String correlationId(HttpServletRequest req) {
        String header = req == null ? null : req.getHeader("X-Correlation-Id");
        return (header == null || header.isBlank()) ? UUID.randomUUID().toString() : header;
    }
}
