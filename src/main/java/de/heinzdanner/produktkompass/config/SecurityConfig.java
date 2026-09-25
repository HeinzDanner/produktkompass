package de.heinzdanner.produktkompass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Erste Filterkette: alles unter /api/**.
     * Hier wird HTTP Basic Auth genutzt (praktisch zum Testen mit
     * Postman/curl); bei fehlender/ungültiger Anmeldung kommt korrekt
     * ein 401 zurück statt einer Weiterleitung auf die Login-Seite.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/register").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/**",
                                "/api/v1/brands/**",
                                "/api/v1/categories/**",
                                "/api/v1/companies/**",
                                "/api/v1/sources/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/products",
                                "/api/v1/brands",
                                "/api/v1/categories",
                                "/api/v1/companies",
                                "/api/v1/sources")
                        .hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/products/**",
                                "/api/v1/brands/**",
                                "/api/v1/categories/**",
                                "/api/v1/companies/**",
                                "/api/v1/sources/**")
                        .hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/products/**",
                                "/api/v1/brands/**",
                                "/api/v1/categories/**",
                                "/api/v1/companies/**",
                                "/api/v1/sources/**")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Zweite Filterkette: alle übrigen (Web-/Thymeleaf-)Anfragen.
     * Hier greift ausschließlich Formular-Login; nicht angemeldete
     * Benutzer werden korrekt auf /login umgeleitet statt einen
     * HTTP-401-Basic-Auth-Dialog zu erhalten.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/", "/products", "/products/**", "/brands/**", "/companies/**",
                                "/login", "/register", "/error", "/css/**", "/js/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/register").permitAll()
                        .requestMatchers("/admin/users", "/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/admin", "/admin/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers("/favorites", "/favorites/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.loginPage("/login").permitAll()
                        .failureHandler(new LoginFailureHandler()))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll());

        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
