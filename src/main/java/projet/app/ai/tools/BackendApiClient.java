package projet.app.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Thin HTTP client that the AI tool layer uses to invoke our existing REST API
 * (Parameters, Ratios, Dashboard, Stress-Test) via a loopback to {@code backend.base-url}.
 *
 * <p>This indirection (HTTP loopback rather than calling Spring services directly)
 * is intentional: it preserves a clean architectural boundary between the AI
 * orchestration plane and the financial-execution plane. If we ever extract the
 * AI module into its own deployable, only this client needs to be reconfigured.
 *
 * <p>We use {@link RestClient} (Spring 6.1+, synchronous) rather than
 * {@code WebClient} because the host application runs on the servlet stack and
 * pulling in Reactor would be unnecessary complexity.
 */
@Slf4j
@Component
public class BackendApiClient {

    private final RestClient restClient;

    public BackendApiClient(@Value("${backend.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
        log.info("BackendApiClient initialised against {}", baseUrl);
    }

    public <T> T get(String path, Class<T> responseType) {
        try {
            return restClient.get().uri(path)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException ex) {
            throw new BackendCallException("GET " + path + " failed: " + ex.getMessage(), ex);
        }
    }

    public <T> T get(String path, ParameterizedTypeReference<T> typeRef) {
        try {
            return restClient.get().uri(path)
                    .retrieve()
                    .body(typeRef);
        } catch (RestClientException ex) {
            throw new BackendCallException("GET " + path + " failed: " + ex.getMessage(), ex);
        }
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        try {
            RestClient.RequestBodySpec spec = restClient.post().uri(path);
            if (body != null) {
                spec.body(body);
            }
            return spec.retrieve().body(responseType);
        } catch (RestClientException ex) {
            throw new BackendCallException("POST " + path + " failed: " + ex.getMessage(), ex);
        }
    }

    public <T> T post(String path, Object body, ParameterizedTypeReference<T> typeRef) {
        try {
            RestClient.RequestBodySpec spec = restClient.post().uri(path);
            if (body != null) {
                spec.body(body);
            }
            return spec.retrieve().body(typeRef);
        } catch (RestClientException ex) {
            throw new BackendCallException("POST " + path + " failed: " + ex.getMessage(), ex);
        }
    }

    /** Raised when the loopback REST call to the existing backend fails. */
    public static class BackendCallException extends RuntimeException {
        public BackendCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Marker bean to keep IDE happy if a {@link RestClientCustomizer} is wanted later;
     * currently unused but referenced to prevent removal by IDE auto-cleanup.
     */
    @SuppressWarnings("unused")
    private static RestClientCustomizer noopCustomizer() {
        return builder -> {};
    }
}
