package com.htttdn.hrm.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.htttdn.hrm.ratelimit.ClientIpResolver;
import com.htttdn.hrm.ratelimit.IpRateLimitFilter;
import com.htttdn.hrm.ratelimit.RateLimitResponses;
import com.htttdn.hrm.ratelimit.RateLimitService;
import com.htttdn.hrm.security.RestAccessDeniedHandler;
import com.htttdn.hrm.security.RestAuthenticationEntryPoint;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
        RestAuthenticationEntryPoint authenticationEntryPoint,
        RestAccessDeniedHandler accessDeniedHandler,
        RateLimitService rateLimitService,
        ClientIpResolver clientIpResolver
    ) throws Exception {
        return http
            .cors(Customizer.withDefaults())
            // After CORS so a 429 still carries the CORS headers, before authentication.
            .addFilterAfter(new IpRateLimitFilter(rateLimitService, clientIpResolver), CorsFilter.class)
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/account-activations/*/complete").permitAll()
                // Company name and logo for the sign-in page.
                .requestMatchers(HttpMethod.GET, "/api/public/branding", "/api/public/branding/logo").permitAll()
                // OpenAPI spec and Swagger UI; turned off with API_DOCS_ENABLED=false.
                .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            )
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${cors.allowed-origins:http://localhost:3000}") List<String> allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        List<String> exposedHeaders = new ArrayList<>(List.of(HttpHeaders.AUTHORIZATION));
        exposedHeaders.addAll(RateLimitResponses.EXPOSED_HEADERS);
        configuration.setExposedHeaders(exposedHeaders);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
