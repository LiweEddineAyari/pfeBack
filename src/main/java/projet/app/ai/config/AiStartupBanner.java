package projet.app.ai.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import projet.app.ai.agent.FinancialAiService;

/**
 * Logs a single-line summary of the AI module's runtime status at startup.
 * Helps operators distinguish between "no API key, AI disabled" vs. "AI is up".
 * Has no side-effect on behaviour.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiStartupBanner {

    private final ObjectProvider<StreamingChatLanguageModel> streamingModelProvider;
    private final ObjectProvider<ChatLanguageModel> chatModelProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final ObjectProvider<FinancialAiService> aiServiceProvider;

    @Value("${backend.base-url}") String backendBaseUrl;
    @Value("${ai.memory.max-messages-window:20}") int memoryWindow;
    @Value("${ai.rag.top-k:8}") int ragTopK;

    @PostConstruct
    public void logStatus() {
        boolean streaming = streamingModelProvider.getIfAvailable() != null;
        boolean aux = chatModelProvider.getIfAvailable() != null;
        boolean embed = embeddingModelProvider.getIfAvailable() != null;
        boolean wired = aiServiceProvider.getIfAvailable() != null;

        log.info("================ AI Module Status ================");
        log.info("FinancialAiService     : {}", wired ? "READY" : "DISABLED (set OPENAI_API_KEY)");
        log.info("Streaming chat model   : {}", streaming ? "yes" : "no");
        log.info("Auxiliary chat model   : {}", aux ? "yes" : "no");
        log.info("Embedding model        : {}", embed ? "yes" : "no");
        log.info("Backend loopback       : {}", backendBaseUrl);
        log.info("Memory window          : {} msgs", memoryWindow);
        log.info("RAG top-k              : {}", ragTopK);
        log.info("===================================================");
    }
}
