package de.heinzdanner.produktkompass.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class SecurityRequestRateLimitFilter extends OncePerRequestFilter {
    private enum Operation {
        LOGIN(20, Duration.ofMinutes(1)),
        REGISTER(5, Duration.ofMinutes(15)),
        REPORT(10, Duration.ofMinutes(15)),
        RECOVERY_REQUEST(5, Duration.ofMinutes(15)),
        RECOVERY_TOKEN(10, Duration.ofMinutes(1)),
        RECOVERY_EMAIL(5, Duration.ofMinutes(15));

        private final int limit;
        private final long windowMillis;

        Operation(int limit, Duration window) {
            this.limit = limit;
            this.windowMillis = window.toMillis();
        }
    }

    private record Key(String address, Operation operation) {}
    private record Window(int count, long expiresAt) {}

    private final boolean enabled;
    private final Clock clock;
    private final int capacity;
    private final Map<Key, Window> windows = new HashMap<>();
    private long nextCleanup;
    private long earliestExpiry = Long.MAX_VALUE;

    public SecurityRequestRateLimitFilter(boolean enabled) {
        this(enabled, Clock.systemUTC(), 10_000);
    }

    SecurityRequestRateLimitFilter(boolean enabled, Clock clock, int capacity) {
        this.enabled = enabled;
        this.clock = clock;
        this.capacity = capacity;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = new UrlPathHelper().getPathWithinApplication(request);
        if (path.equals("/forgot-password") || path.equals("/reset-password") || path.equals("/verify-email")
                || path.startsWith("/account/security")) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setHeader("Content-Security-Policy",
                    "default-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        }
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }
        Operation operation = operation(request, path);
        long retryAfter = operation == null ? 0 : consume(new Key(request.getRemoteAddr(), operation));
        if (retryAfter == 0) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Zu viele Anfragen. Bitte in " + retryAfter + " Sekunden erneut versuchen.");
    }

    private Operation operation(HttpServletRequest request, String path) {
        if ("POST".equals(request.getMethod())) {
            if (path.equals("/login")) return Operation.LOGIN;
            if (path.equals("/register") || path.equals("/api/v1/users/register")) return Operation.REGISTER;
            if (path.matches("/products/[^/]+/report")) return Operation.REPORT;
            if (path.equals("/forgot-password")) return Operation.RECOVERY_REQUEST;
            if (path.equals("/reset-password") || path.equals("/verify-email")) return Operation.RECOVERY_TOKEN;
            if (path.equals("/account/security/email")) return Operation.RECOVERY_EMAIL;
        }
        String authorization = request.getHeader("Authorization");
        if (path.startsWith("/api/") && authorization != null
                && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Operation.LOGIN;
        }
        return null;
    }

    private synchronized long consume(Key key) {
        long now = clock.millis();
        if (now >= nextCleanup) {
            windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            earliestExpiry = windows.values().stream().mapToLong(Window::expiresAt).min().orElse(Long.MAX_VALUE);
            nextCleanup = Math.min(now + Duration.ofMinutes(1).toMillis(), earliestExpiry);
        }
        Window window = windows.get(key);
        if (window == null || window.expiresAt() <= now) {
            if (window == null && windows.size() >= capacity) {
                // Do not evict active limits, which would let new addresses reset existing quotas.
                return secondsUntil(now, earliestExpiry);
            }
            long expiresAt = now + key.operation().windowMillis;
            windows.put(key, new Window(1, expiresAt));
            earliestExpiry = Math.min(earliestExpiry, expiresAt);
            nextCleanup = Math.min(nextCleanup, expiresAt);
            return 0;
        }
        if (window.count() >= key.operation().limit) return secondsUntil(now, window.expiresAt());
        windows.put(key, new Window(window.count() + 1, window.expiresAt()));
        return 0;
    }

    private long secondsUntil(long now, long expiresAt) {
        return Math.max(1, (expiresAt - now + 999) / 1000);
    }
}
