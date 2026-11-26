package de.heinzdanner.produktkompass.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "produktkompass.security.rate-limiting.enabled=true")
class SecurityRateLimitIntegrationTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void unknownLoginsAndBasicAuthenticationShareLimitAcrossSecurityChains() throws Exception {
        String address = "192.0.2.1";
        for (int i = 0; i < 20; i++) {
            if (i % 2 == 0) {
                mvc.perform(post("/login").with(csrf()).param("username", "unknown-" + i)
                                .param("password", "invalid").with(req -> { req.setRemoteAddr(address); return req; }))
                        .andExpect(status().is3xxRedirection());
            } else {
                mvc.perform(get("/api/v1/users/me").with(httpBasic("unknown-" + i, "invalid"))
                                .with(req -> { req.setRemoteAddr(address); return req; }))
                        .andExpect(status().isUnauthorized());
            }
        }
        mvc.perform(post("/login").with(csrf()).with(req -> { req.setRemoteAddr(address); return req; }))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        mvc.perform(get("/api/v1/users/me").with(httpBasic("unknown", "invalid"))
                        .with(req -> { req.setRemoteAddr(address); return req; }))
                .andExpect(status().isTooManyRequests());
        mvc.perform(get("/products").with(req -> { req.setRemoteAddr(address); return req; }))
                .andExpect(status().isOk());
    }

    @Test
    void registrationAndReportWritesAreLimitedEvenWithoutCsrf() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post(i % 2 == 0 ? "/register" : "/api/v1/users/register")
                            .with(req -> { req.setRemoteAddr("192.0.2.2"); return req; }))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/register").with(req -> { req.setRemoteAddr("192.0.2.2"); return req; }))
                .andExpect(status().isTooManyRequests());
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/products/" + i + "/report")
                            .with(req -> { req.setRemoteAddr("192.0.2.3"); return req; }))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/products/999/report").with(req -> { req.setRemoteAddr("192.0.2.3"); return req; }))
                .andExpect(status().isTooManyRequests());
    }
}
