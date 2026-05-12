package projet.app.ai.chat.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.ai.chat.dto.ChatSessionDTO;
import projet.app.ai.memory.entity.ChatSessionEntity;
import projet.app.ai.memory.repository.ChatSessionRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the lifecycle of a chat session: resolution / creation, title generation
 * after the first user turn, archive, and "touch" updates after each AI response.
 *
 * <p>Title generation runs asynchronously (when {@code ai.title-generation.async=true})
 * via a separate, non-streaming {@link ChatLanguageModel} so the user never waits.
 */
@Slf4j
@Service
public class SessionService {

    private final ChatSessionRepository sessionRepository;
    private final ObjectProvider<ChatLanguageModel> titleModelProvider;
    private final boolean titleEnabled;

    public SessionService(ChatSessionRepository sessionRepository,
                          ObjectProvider<ChatLanguageModel> titleModelProvider,
                          @Value("${ai.title-generation.enabled:true}") boolean titleEnabled) {
        this.sessionRepository = sessionRepository;
        this.titleModelProvider = titleModelProvider;
        this.titleEnabled = titleEnabled;
    }

    @Transactional
    public ChatSessionEntity resolveOrCreate(String maybeSessionId, String userId) {
        if (maybeSessionId != null && !maybeSessionId.isBlank()) {
            try {
                UUID id = UUID.fromString(maybeSessionId);
                Optional<ChatSessionEntity> existing = sessionRepository.findById(id);
                if (existing.isPresent()) {
                    return existing.get();
                }
            } catch (IllegalArgumentException ignored) {
                log.warn("Invalid sessionId='{}', creating fresh session.", maybeSessionId);
            }
        }
        return createSession(userId);
    }

    @Transactional
    public ChatSessionEntity createSession(String userId) {
        Instant now = Instant.now();
        ChatSessionEntity entity = ChatSessionEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId == null || userId.isBlank() ? "anonymous" : userId)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .lastMessageAt(now)
                .build();
        return sessionRepository.save(entity);
    }

    @Transactional
    public void touch(UUID sessionId) {
        sessionRepository.touch(sessionId, Instant.now());
    }

    @Transactional
    public void archive(UUID sessionId) {
        sessionRepository.archive(sessionId, Instant.now());
    }

    @Transactional
    public void delete(UUID sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    @Transactional
    public void updateTitle(UUID sessionId, String title) {
        sessionRepository.updateTitle(sessionId, title, Instant.now());
    }

    @Transactional(readOnly = true)
    public Page<ChatSessionDTO> listForUser(String userId, Pageable pageable) {
        return sessionRepository
                .findByUserIdAndStatusOrderByLastMessageAtDesc(userId, "ACTIVE", pageable)
                .map(ChatSessionDTO::from);
    }

    @Transactional(readOnly = true)
    public Optional<ChatSessionEntity> findById(UUID id) {
        return sessionRepository.findById(id);
    }

    /**
     * Trigger an asynchronous title generation. We use a cheap, non-streaming model
     * and cap the title at ~6 words; the result is persisted and surfaced via SSE
     * on the next chat completion.
     *
     * <p>Note: {@code @Transactional} is required because {@link
     * ChatSessionRepository#updateTitle} is a {@code @Modifying} JPQL query — JPA
     * refuses to execute UPDATE/DELETE queries outside a managed transaction
     * ("Executing an update/delete query"). The {@code @Async} method runs in a
     * separate thread, so a fresh transaction must be opened by the proxy here.
     */
    @Async
    @Transactional
    public void generateTitleIfMissing(UUID sessionId, String firstUserMessage) {
        if (!titleEnabled) {
            return;
        }
        Optional<ChatSessionEntity> opt = sessionRepository.findById(sessionId);
        if (opt.isEmpty() || (opt.get().getTitle() != null && !opt.get().getTitle().isBlank())) {
            return;
        }
        ChatLanguageModel titleModel = titleModelProvider.getIfAvailable();
        if (titleModel == null) {
            log.debug("No title model bean available; skipping title generation.");
            return;
        }
        String prompt = """
                Generate a concise 3-6 word title for a banking analyst chat session.
                Capture the financial topic of this first question. Respond with ONLY the
                title text — no quotes, no punctuation, no leading verbs like "Title:".
                Use the SAME language as the question.

                Question: %s
                """.formatted(firstUserMessage);
        try {
            String title = titleModel.generate(prompt);
            if (title == null) {
                return;
            }
            String cleaned = title.trim().replaceAll("^[\"'\\s]+|[\"'\\s]+$", "");
            if (cleaned.length() > 120) {
                cleaned = cleaned.substring(0, 120);
            }
            sessionRepository.updateTitle(sessionId, cleaned, Instant.now());
            log.info("Session {} titled: {}", sessionId, cleaned);
        } catch (RuntimeException ex) {
            log.warn("Title generation failed for session {}: {}", sessionId, ex.getMessage());
        }
    }
}
