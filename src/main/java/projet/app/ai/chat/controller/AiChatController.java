package projet.app.ai.chat.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.TokenStream;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import projet.app.ai.agent.FinancialAiService;
import projet.app.ai.chat.dto.ChatRequestDTO;
import projet.app.ai.chat.service.EntityExtractorService;
import projet.app.ai.chat.service.SessionService;
import projet.app.ai.memory.entity.ChatSessionEntity;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The single user-facing chat endpoint. Streams GPT-4o tokens to the React
 * frontend via Server-Sent Events. Event types emitted:
 *
 * <pre>
 *   event: session       data: {"sessionId":"&lt;uuid&gt;","isNew":true|false}
 *   event: token         data: {"text":"next chunk of streamed text"}
 *   event: tool_executed data: {"name":"compare_ratio_across_dates",
 *                               "args":   { ...parsed JSON object... },
 *                               "result": { ...parsed JSON object... }}
 *   event: done          data: {"sessionId":"&lt;uuid&gt;","finishReason":"STOP"}
 *   event: error         data: {"message":"..."}
 * </pre>
 *
 * <p>Both {@code args} and {@code result} are emitted as parsed JSON when the
 * underlying LangChain4j payload is valid JSON; otherwise they fall back to a
 * plain string so consumers don't have to second-guess the type.</p>
 *
 * <p>Authentication is intentionally minimal: we accept an {@code X-User-Id}
 * header (or fall back to {@code "anonymous"}). Plug a real JWT filter in
 * front of this controller when the rest of the platform gets auth.
 */
@Slf4j
@RestController
@RequestMapping({"/ai", "/api/ai"})
@RequiredArgsConstructor
public class AiChatController {

    private static final long SSE_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

    private final ObjectProvider<FinancialAiService> aiServiceProvider;
    private final SessionService sessionService;
    private final EntityExtractorService entityExtractorService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @Valid @RequestBody ChatRequestDTO request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        FinancialAiService aiService = aiServiceProvider.getIfAvailable();
        if (aiService == null) {
            sendError(emitter, "OpenAI is not configured: set the OPENAI_API_KEY environment variable.");
            emitter.complete();
            return emitter;
        }

        String userId = (userIdHeader == null || userIdHeader.isBlank())
                ? "anonymous" : userIdHeader.trim();
        ChatSessionEntity session = sessionService.resolveOrCreate(request.getSessionId(), userId);
        UUID sessionId = session.getId();
        boolean isNewSession = session.getTitle() == null || session.getTitle().isBlank();

        sendEvent(emitter, "session",
                Map.of("sessionId", sessionId.toString(), "isNew", isNewSession));

        AtomicBoolean closed = new AtomicBoolean(false);
        TokenStream stream = aiService.chat(sessionId.toString(), request.getMessage());

        stream
                .onNext(token -> {
                    if (closed.get() || token == null) return;
                    sendEvent(emitter, "token", Map.of("text", token));
                })
                .onToolExecuted(toolExecution -> {
                    if (closed.get() || toolExecution == null) return;
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("name", toolExecution.request().name());
                    payload.put("args", parseJsonOrString(toolExecution.request().arguments()));
                    payload.put("result", parseJsonOrString(toolExecution.result()));
                    sendEvent(emitter, "tool_executed", payload);
                })
                .onComplete(response -> {
                    try {
                        sessionService.touch(sessionId);
                        if (isNewSession) {
                            sessionService.generateTitleIfMissing(sessionId, request.getMessage());
                        }
                        entityExtractorService.extractAndPersist(sessionId, request.getMessage());
                        if (response != null && response.content() != null) {
                            entityExtractorService.extractAndPersist(
                                    sessionId, response.content().text());
                        }
                        sendEvent(emitter, "done", Map.of(
                                "sessionId", sessionId.toString(),
                                "finishReason", finishReason(response)));
                    } finally {
                        closeQuietly(emitter, closed);
                    }
                })
                .onError(err -> {
                    log.warn("LLM chat error in session {}: {}", sessionId, err.toString());
                    sendError(emitter, err.getMessage());
                    closeQuietly(emitter, closed);
                });

        emitter.onTimeout(() -> {
            log.warn("SSE timeout for session {}", sessionId);
            closeQuietly(emitter, closed);
        });
        emitter.onError(err -> {
            log.warn("SSE transport error for session {}: {}", sessionId, err.getMessage());
            closeQuietly(emitter, closed);
        });

        try {
            stream.start();
        } catch (RuntimeException ex) {
            log.error("Unable to start LLM stream", ex);
            sendError(emitter, ex.getMessage());
            closeQuietly(emitter, closed);
        }
        return emitter;
    }

    /** A non-streaming health probe so the frontend can verify the AI module is wired. */
    @GetMapping(value = "/chat/ping", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ping() {
        boolean ready = aiServiceProvider.getIfAvailable() != null;
        return ResponseEntity.ok(Map.of(
                "ready", ready,
                "message", ready ? "FinancialAiService is wired."
                        : "OpenAI key missing — set OPENAI_API_KEY to enable AI."));
    }

    // ─── SSE plumbing ───────────────────────────────────────────────────────────

    private void sendEvent(SseEmitter emitter, String event, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(event).data(json));
        } catch (IOException | IllegalStateException ex) {
            log.debug("SSE send '{}' failed: {}", event, ex.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        sendEvent(emitter, "error",
                Map.of("message", message == null ? "Unknown error" : message));
    }

    private void closeQuietly(SseEmitter emitter, AtomicBoolean closed) {
        if (closed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // Already closed by client / framework, nothing to do.
            }
        }
    }

    private static String finishReason(Response<AiMessage> response) {
        if (response == null || response.finishReason() == null) {
            return "unknown";
        }
        return response.finishReason().name();
    }

    /**
     * Best-effort JSON parsing for tool-call arguments / results. When the input
     * is a valid JSON object or array we return a {@link JsonNode} so Jackson
     * serialises it as a real object on the wire (no double-escaped quotes).
     * For non-JSON payloads we return the original string unchanged so the
     * consumer always gets a clean, readable value.
     */
    private Object parseJsonOrString(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return raw;
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            return node;
        } catch (JsonProcessingException ex) {
            return raw;
        }
    }
}
