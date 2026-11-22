package com.aiinterview.config;

import com.aiinterview.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean JwtEncoder jwtEncoder(AppProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(properties.jwtSecret().getBytes(StandardCharsets.UTF_8)));
    }
    @Bean JwtDecoder jwtDecoder(AppProperties properties) {
        var decoder=NimbusJwtDecoder.withSecretKey(new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("ai-interview"));
        return decoder;
    }
    @Bean SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter filter, ObjectMapper json) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c -> c.requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.svg", "/assets/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/", "/index.html", "/favicon.svg", "/assets/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/training/**", "/api/voice/**", "/api/formal-interviews", "/api/formal-interviews/**", "/api/reports/**").hasRole("STUDENT")
                        .anyRequest().authenticated())
                .exceptionHandling(c -> c.authenticationEntryPoint((req,res,e) -> {
                    res.setStatus(401); res.setContentType(MediaType.APPLICATION_JSON_VALUE); res.setCharacterEncoding("UTF-8");
                    json.writeValue(res.getWriter(), ApiResponse.error("AUTH_REQUIRED", "请先登录"));
                }).accessDeniedHandler((req,res,e) -> {
                    res.setStatus(403); res.setContentType(MediaType.APPLICATION_JSON_VALUE); res.setCharacterEncoding("UTF-8");
                    json.writeValue(res.getWriter(), ApiResponse.error("PERMISSION_DENIED", "无操作权限"));
                })).addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
