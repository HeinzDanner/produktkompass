package de.heinzdanner.produktkompass.config;

import de.heinzdanner.produktkompass.user.AppUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/", "/products", "/favorites", "/login", "/error", "/css/**", "/js/**").permitAll()
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
                        .requestMatchers("/favorites/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.loginPage("/login").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/").permitAll())
                .httpBasic(Customizer.withDefaults());

        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
