package projet.app.ai.chat.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.ai.memory.entity.ChatMessageEntity;
import projet.app.ai.memory.entity.ChatSummaryEntity;
import projet.app.ai.memory.repository.ChatMessageRepository;
import projet.app.ai.memory.repository.ChatSummaryRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Periodically rolls up long sessions into compact natural-language summaries
 * stored in {@code ai.chat_summaries}, then trims the per-session message log
 * down to the last N entries to keep the LLM context window small.
 *
 * <p>This runs on a single shared Spring scheduler thread; expensive work
 * (LLM call) per session is sequential to avoid hammering OpenAI rate limits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummarizationService {

    private final ChatMessageRepository messageRepository;
    private final ChatSummaryRepository summaryRepository;
    private final ObjectProvider<ChatLanguageModel> summarizationModelProvider;

    @Value("${ai.memory.summarize-every-n-turns:10}")
    private int summarizeThreshold;

    @Value("${ai.memory.max-messages-window:20}")
    private int retainAfterSummary;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void summarizeLongSessions() {
        ChatLanguageModel model = summarizationModelProvider.getIfAvailable();
        if (model == null) {
            return;
        }
        List<String> sessionIds = messageRepository.findSessionsNeedingSummary(summarizeThreshold);
        if (sessionIds.isEmpty()) {
            return;
        }
        log.info("Summarizing {} long session(s)", sessionIds.size());
        for (String sid : sessionIds) {
            try {
                summarizeSession(UUID.fromString(sid), model);
            } catch (RuntimeException ex) {
                log.warn("Summarization failed for session {}: {}", sid, ex.getMessage());
            }
        }
    }

    @Transactional
    protected void summarizeSession(UUID sessionId, ChatLanguageModel model) {
        List<ChatMessageEntity> messages = messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);
        if (messages.size() < summarizeThreshold) {
            return;
        }
        String conversation = messages.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        String summary = model.generate("""
                Summarize this banking analyst conversation concisely (under 400 words).
                Cover: main ratios/parameters discussed, key values, threshold breaches found,
                stress-test scenarios run, recommendations given, open questions.
                Use bullet points per topic. Same language as the conversation.

                Conversation:
                %s
                """.formatted(conversation));

        summaryRepository.save(ChatSummaryEntity.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .summary(summary)
                .turnCount(messages.size())
                .createdAt(Instant.now())
                .build());

        messageRepository.trimToLastN(sessionId, retainAfterSummary);
    }
}
