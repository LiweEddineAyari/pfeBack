package projet.app.ai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import projet.app.ai.memory.entity.ChatMessageEntity;
import projet.app.ai.memory.repository.ChatMessageRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JPA-backed implementation of LangChain4j's {@link ChatMemoryStore}.
 *
 * <p>LangChain4j passes the FULL message list to {@link #updateMessages} after each
 * turn, so we implement upsert as "delete-then-reinsert in deterministic order".
 * This keeps the persisted ordering aligned with the in-memory window kept by
 * {@code MessageWindowChatMemory}.
 *
 * <p>The {@code memoryId} that LangChain4j passes is the session UUID as a string
 * (we control that contract from the controller / aiService.chat call).
 *
 * <p>{@code SystemMessage}s are intentionally NOT persisted — the framework
 * re-injects them from the {@code @SystemMessage} annotation on every turn, so
 * persisting them would create duplicates after a restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(Object memoryId) {
        UUID sessionId = parse(memoryId);
        if (sessionId == null) {
            return List.of();
        }
        List<ChatMessageEntity> rows = repository.findBySessionIdOrderBySequenceNoAsc(sessionId);
        List<ChatMessage> result = new ArrayList<>(rows.size());
        for (ChatMessageEntity row : rows) {
            ChatMessage msg = toChatMessage(row);
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        UUID sessionId = parse(memoryId);
        if (sessionId == null) {
            log.warn("updateMessages: invalid memoryId={}", memoryId);
            return;
        }
        repository.deleteBySessionId(sessionId);

        Instant now = Instant.now();
        long seq = 0;
        for (ChatMessage msg : messages) {
            ChatMessageEntity entity = toEntity(sessionId, msg, ++seq, now);
            if (entity != null) {
                repository.save(entity);
            }
        }
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        UUID sessionId = parse(memoryId);
        if (sessionId == null) {
            return;
        }
        repository.deleteBySessionId(sessionId);
    }

    // ─── conversions ────────────────────────────────────────────────────────────

    private ChatMessage toChatMessage(ChatMessageEntity row) {
        return switch (row.getRole()) {
            case "USER" -> UserMessage.from(row.getContent());
            case "AI" -> AiMessage.from(row.getContent());
            case "SYSTEM" -> SystemMessage.from(row.getContent());
            case "TOOL_EXECUTION" -> ToolExecutionResultMessage.from(
                    null, row.getToolName(), row.getContent());
            default -> null;
        };
    }

    private ChatMessageEntity toEntity(UUID sessionId, ChatMessage msg, long seq, Instant now) {
        if (msg instanceof SystemMessage) {
            return null;
        }
        String role;
        String content;
        String toolName = null;
        String toolInput = null;
        String toolOutput = null;

        if (msg instanceof UserMessage um) {
            role = "USER";
            content = um.singleText();
        } else if (msg instanceof AiMessage am) {
            role = "AI";
            content = am.text() == null ? "" : am.text();
            if (am.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> reqs = am.toolExecutionRequests();
                toolName = reqs.get(0).name();
                List<Map<String, Object>> simplified = new ArrayList<>(reqs.size());
                for (ToolExecutionRequest r : reqs) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", r.id());
                    entry.put("name", r.name());
                    entry.put("arguments", r.arguments());
                    simplified.add(entry);
                }
                try {
                    toolInput = objectMapper.writeValueAsString(simplified);
                } catch (JsonProcessingException e) {
                    log.warn("Could not serialise tool requests: {}", e.getMessage());
                }
            }
        } else if (msg instanceof ToolExecutionResultMessage tem) {
            role = "TOOL_EXECUTION";
            content = tem.text() == null ? "" : tem.text();
            toolName = tem.toolName();
            toolOutput = content;
        } else {
            log.debug("Skipping unsupported message kind: {}", msg.getClass().getSimpleName());
            return null;
        }

        return ChatMessageEntity.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .toolName(toolName)
                .toolInput(toolInput)
                .toolOutput(toolOutput)
                .sequenceNo(seq)
                .createdAt(now)
                .build();
    }

    private static UUID parse(Object memoryId) {
        if (memoryId == null) {
            return null;
        }
        try {
            return memoryId instanceof UUID uuid ? uuid : UUID.fromString(memoryId.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
