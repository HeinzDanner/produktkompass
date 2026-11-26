package de.heinzdanner.produktkompass.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityRequestRateLimitFilterTest {
    @Test
    void loginLimitsAtExactThresholdAndWindowExpiresWithoutExtendingOnRejection() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        var filter = new SecurityRequestRateLimitFilter(true, clock, 100);
        for (int i = 0; i < 20; i++) assertThat(call(filter, "/login", null, "a").getStatus()).isEqualTo(200);
        var blocked = call(filter, "/api/v1/users/me", "Basic invalid", "a");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("Zu viele Anfragen");
        when(clock.millis()).thenReturn(59_001L);
        assertThat(call(filter, "/login", null, "a").getHeader("Retry-After")).isEqualTo("1");
        when(clock.millis()).thenReturn(60_000L);
        assertThat(call(filter, "/login", null, "a").getStatus()).isEqualTo(200);
    }

    @Test
    void registrationAndReportsHaveIndependentQuotasSharedAcrossRoutesAndProductIds() throws Exception {
        var filter = new SecurityRequestRateLimitFilter(true);
        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, i % 2 == 0 ? "/register" : "/api/v1/users/register", null, "a").getStatus())
                    .isEqualTo(200);
        }
        assertThat(call(filter, "/api/v1/users/register", null, "a").getStatus()).isEqualTo(429);
        for (int i = 0; i < 10; i++) {
            assertThat(call(filter, "/products/" + i + "/report", null, "a").getStatus()).isEqualTo(200);
        }
        assertThat(call(filter, "/products/999/report", null, "a").getStatus()).isEqualTo(429);
        assertThat(call(filter, "/login", null, "a").getStatus()).isEqualTo(200);
        assertThat(call(filter, "/register", null, "b").getStatus()).isEqualTo(200);
        assertThat(call(filter, "/products/1", null, "a").getStatus()).isEqualTo(200);
    }

    @Test
    void recoveryActionsHaveSeparateLimitsAndSensitivePagesNeverExposeReferrers() throws Exception {
        var filter = new SecurityRequestRateLimitFilter(true);
        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "/forgot-password", null, "a").getStatus()).isEqualTo(200);
            assertThat(call(filter, "/account/security/email", null, "a").getStatus()).isEqualTo(200);
        }
        assertThat(call(filter, "/forgot-password", null, "a").getStatus()).isEqualTo(429);
        assertThat(call(filter, "/account/security/email", null, "a").getStatus()).isEqualTo(429);
        for (int i = 0; i < 10; i++) {
            var response = call(filter, i % 2 == 0 ? "/reset-password" : "/verify-email", null, "a");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(response.getHeader("Content-Security-Policy")).contains("form-action 'self'");
        }
        assertThat(call(filter, "/reset-password", null, "a").getStatus()).isEqualTo(429);
        assertThat(call(filter, "/verify-email", null, "a").getStatus()).isEqualTo(429);
    }

    @Test
    void parallelRequestsCannotExceedQuota() throws Exception {
        var filter = new SecurityRequestRateLimitFilter(true);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Integer>>();
            for (int i = 0; i < 100; i++) {
                tasks.add(() -> call(filter, "/login", null, "a").getStatus());
            }
            int allowed = 0;
            int rejected = 0;
            for (var result : executor.invokeAll(tasks)) {
                int status = result.get();
                if (status == 200) allowed++;
                if (status == 429) rejected++;
            }
            assertThat(allowed).isEqualTo(20);
            assertThat(rejected).isEqualTo(80);
        }
    }

    @Test
    void expiredEntriesFreeCapacityAndDisabledFilterDoesNotLimitRequests() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        var filter = new SecurityRequestRateLimitFilter(true, clock, 1);
        assertThat(call(filter, "/login", null, "a").getStatus()).isEqualTo(200);
        assertThat(call(filter, "/login", null, "b").getStatus()).isEqualTo(429);
        when(clock.millis()).thenReturn(60_000L);
        assertThat(call(filter, "/login", null, "b").getStatus()).isEqualTo(200);
        var disabled = new SecurityRequestRateLimitFilter(false);
        for (int i = 0; i < 25; i++) assertThat(call(disabled, "/login", null, "a").getStatus()).isEqualTo(200);
    }

    @Test
    void forwardedHeaderCannotResetQuotaAndCacheCannotEvictActiveQuota() throws Exception {
        var filter = new SecurityRequestRateLimitFilter(true, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), 1);
        assertThat(call(filter, "/login", null, "a").getStatus()).isEqualTo(200);
        assertThat(call(filter, "/login", null, "b").getStatus()).isEqualTo(429);
        for (int i = 1; i < 20; i++) assertThat(call(filter, "/login", null, "a").getStatus()).isEqualTo(200);
        assertThat(call(filter, "/login", null, "a").getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse call(SecurityRequestRateLimitFilter filter, String path,
                                          String authorization, String address) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(address);
        request.addHeader("X-Forwarded-For", java.util.UUID.randomUUID().toString());
        if (authorization != null) request.addHeader("Authorization", authorization);
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> reached.set(true));
        assertThat(reached.get()).isEqualTo(response.getStatus() == 200);
        return response;
    }
}
