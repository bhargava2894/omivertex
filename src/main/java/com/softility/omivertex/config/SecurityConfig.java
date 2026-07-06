package com.softility.omivertex.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Content-hashed static assets (/assets/**) are safe to cache forever — a content
     * change produces a new filename. This chain overrides Spring Security's default
     * no-store so the browser can cache them; everything else stays no-store.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain assetChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/assets/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .cacheControl(cache -> cache.disable())
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Cache-Control", "public, max-age=31536000, immutable")));
        return http.build();
    }

    /**
     * Two internal accounts: the super admin (full edit access) and a read-only
     * viewer. Passwords are overridable via properties for real deployments.
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${omivertex.auth.admin-password:Admin@123}") String adminPassword,
            @Value("${omivertex.auth.viewer-password:Viewer@123}") String viewerPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("admin").password("{noop}" + adminPassword).roles("ADMIN").build(),
                User.withUsername("viewer").password("{noop}" + viewerPassword).roles("VIEWER").build());
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/google").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("ADMIN", "VIEWER")
                        .requestMatchers("/api/v1/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Authentication required\",\"fieldErrors\":{}}");
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Your account is read-only\",\"fieldErrors\":{}}");
                        }));
        return http.build();
    }
}
