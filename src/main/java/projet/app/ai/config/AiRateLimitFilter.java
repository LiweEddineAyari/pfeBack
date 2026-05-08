package projet.app.ai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny sliding-window rate limiter for the AI chat endpoint. Keyed by
 * {@code X-User-Id} (or remote IP if absent). Defaults to 60 requests/minute,
 * easily tuned via {@code ai.rate-limit.requests-per-minute}.
 *
 * <p>This is in-memory only. For multi-node deployments swap in a Redis-backed
 * counter or a sidecar limiter (Bucket4j, Resilience4j, etc.). Kept here to
 * avoid pulling extra dependencies for a bootstrap implementation.
 */
@Slf4j
@Order(10)
@Component
public class AiRateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    private final long windowMillis = 60_000L;

    public AiRateLimitFilter(@Value("${ai.rate-limit.requests-per-minute:60}") int rpm) {
        this.requestsPerMinute = rpm;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null
                || (!path.startsWith("/ai/chat") && !path.startsWith("/ai/rag/ingest"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String key = clientKey(request);
        long now = System.currentTimeMillis();
        Deque<Long> hits = windows.computeIfAbsent(key, k -> new LinkedList<>());

        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < now - windowMillis) {
                hits.pollFirst();
            }
            if (hits.size() >= requestsPerMinute) {
                long retryAfterSec = Math.max(1L,
                        (windowMillis - (now - hits.peekFirst())) / 1000L);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", Long.toString(retryAfterSec));
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests, retry in "
                                + retryAfterSec + "s\"}");
                log.warn("Rate-limited {} on {} ({} req/min)",
                        key, request.getRequestURI(), requestsPerMinute);
                return;
            }
            hits.addLast(now);
        }
        chain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest req) {
        String userId = req.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "u:" + userId.trim();
        }
        return "ip:" + (req.getRemoteAddr() == null ? "unknown" : req.getRemoteAddr());
    }
}
