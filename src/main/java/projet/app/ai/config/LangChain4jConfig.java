package projet.app.ai.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import projet.app.ai.agent.FinancialAiService;
import projet.app.ai.rag.retrieval.HybridFinancialRetriever;
import projet.app.ai.tools.FinancialTools;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Duration;

/**
 * The single composition root for the AI module: instantiates OpenAI models,
 * wires the chat-memory provider, the RAG retrieval augmentor, and finally
 * builds the {@link FinancialAiService} via {@link AiServices}.
 *
 * <p>Every OpenAI-dependent bean is guarded by {@code @ConditionalOnExpression}
 * that checks {@code openai.api-key} is non-blank. This is necessary because
 * the property IS defined in {@code application.properties} (with an empty
 * default), so {@code @ConditionalOnProperty} alone evaluates to {@code true}
 * even when no real key has been provided. The expression guard prevents bean
 * creation failures at startup when running without an API key.
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    // SpEL expression shared by all OpenAI-dependent beans.
    // Evaluates to true only when openai.api-key is defined AND non-blank.
    private static final String OPENAI_KEY_PRESENT =
            "T(org.springframework.util.StringUtils).hasText('${openai.api-key:}')";

    @Bean
    @ConditionalOnExpression(OPENAI_KEY_PRESENT)
    public StreamingChatLanguageModel streamingChatModel(
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.chat-model:gpt-4o}") String modelName,
            @Value("${openai.temperature:0.2}") double temperature,
            @Value("${openai.max-tokens:4096}") int maxTokens,
            @Value("${openai.timeout-seconds:60}") int timeoutSec) {
        log.info("Configuring OpenAI streaming model '{}' (temp={}, maxTokens={})",
                modelName, temperature, maxTokens);
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSec))
                .build();
    }

    /** Non-streaming model for cheap auxiliary tasks (titles, summaries). */
    @Bean
    @ConditionalOnExpression(OPENAI_KEY_PRESENT)
    public ChatLanguageModel auxiliaryChatModel(
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.title-model:gpt-4o-mini}") String modelName,
            @Value("${openai.timeout-seconds:60}") int timeoutSec) {
        log.info("Configuring OpenAI auxiliary model '{}'", modelName);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(timeoutSec))
                .build();
    }

    @Bean
    @ConditionalOnExpression(OPENAI_KEY_PRESENT)
    public EmbeddingModel embeddingModel(
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.embedding-model:text-embedding-3-small}") String modelName,
            @Value("${openai.timeout-seconds:60}") int timeoutSec) {
        log.info("Configuring OpenAI embedding model '{}'", modelName);
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSec))
                .build();
    }

    /**
     * LangChain4j {@link PgVectorEmbeddingStore} backed by {@code rag.documents}.
     * Requires both a non-blank API key AND pgvector installed on PostgreSQL.
     * Construction is wrapped in a try/catch so a missing pgvector installation
     * degrades gracefully without crashing startup.
     */
    @Bean
    @ConditionalOnExpression(OPENAI_KEY_PRESENT)
    public EmbeddingStore<TextSegment> embeddingStore(
            DataSource dataSource,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${ai.rag.embedding-dimension:1536}") int dimension) {
        JdbcUrl parts = JdbcUrl.parse(jdbcUrl);
        try {
            EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
                    .host(parts.host())
                    .port(parts.port())
                    .database(parts.database())
                    .user(username)
                    .password(password)
                    .table("documents")
                    .dimension(dimension)
                    .useIndex(false)    // index created by AiSchemaInitializer
                    .createTable(false) // table created by AiSchemaInitializer
                    .build();
            log.info("PgVectorEmbeddingStore initialised ({}:{}/{})",
                    parts.host(), parts.port(), parts.database());
            return store;
        } catch (Exception ex) {
            log.warn("PgVectorEmbeddingStore unavailable (pgvector not installed?): {} " +
                    "— semantic search disabled, FTS still active.", ex.getMessage());
            return null;
        }
    }

    /** Per-session memory factory — pulls history from the JPA-backed store. */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(
            ChatMemoryStore chatMemoryStore,
            @Value("${ai.memory.max-messages-window:20}") int windowSize) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(windowSize)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    /** The RAG augmentor injected into AiServices. */
    @Bean
    public RetrievalAugmentor retrievalAugmentor(HybridFinancialRetriever retriever) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever((ContentRetriever) retriever)
                .build();
    }

    /**
     * Final composition: streaming model + per-session memory + tools + RAG →
     * {@link FinancialAiService}. Skipped entirely when no API key is present.
     */
    @Bean
    @ConditionalOnExpression(OPENAI_KEY_PRESENT)
    public FinancialAiService financialAiService(
            StreamingChatLanguageModel streamingChatModel,
            ChatMemoryProvider chatMemoryProvider,
            RetrievalAugmentor retrievalAugmentor,
            FinancialTools tools,
            @Value("${ai.orchestrator.max-tool-calls-per-turn:8}") int maxIterations) {
        log.info("Building FinancialAiService (maxIterations={})", maxIterations);
        return AiServices.builder(FinancialAiService.class)
                .streamingChatLanguageModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .retrievalAugmentor(retrievalAugmentor)
                .tools(tools)
                .build();
    }

    /**
     * Standalone {@link ChatMemory} bean (non-session-scoped) useful for
     * single-turn auxiliary calls such as title generation or summarization.
     */
    @Bean
    public ChatMemory defaultChatMemory(@Value("${ai.memory.max-messages-window:20}") int windowSize) {
        return MessageWindowChatMemory.builder()
                .maxMessages(windowSize)
                .build();
    }

    /**
     * Tiny parser for {@code spring.datasource.url} → host / port / database
     * so we can configure {@link PgVectorEmbeddingStore} without duplicating
     * individual host/port properties in {@code application.properties}.
     * Accepts {@code jdbc:postgresql://host:5432/dbname[?params]}.
     */
    private record JdbcUrl(String host, int port, String database) {
        static JdbcUrl parse(String url) {
            String stripped = (url == null ? "" : url).replaceFirst("^jdbc:", "");
            URI uri = URI.create(stripped);
            String host = uri.getHost();
            int port = uri.getPort() < 0 ? 5432 : uri.getPort();
            String path = uri.getPath();
            String db = (path == null || path.length() <= 1) ? "postgres" : path.substring(1);
            int q = db.indexOf('?');
            if (q >= 0) db = db.substring(0, q);
            return new JdbcUrl(host == null ? "localhost" : host, port, db);
        }
    }
}
