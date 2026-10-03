package com.cangshuo.toolbox.common.security;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfiguration {

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(Environment environment) {
        List<String> origins = Arrays.stream(environment.getProperty("toolbox.cors.allowed-origins", "").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        for (String origin : origins) {
            URI uri = URI.create(origin);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || origin.contains("*") || uri.getPort() > 65535) {
                throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS must contain HTTP(S) origins without paths or wildcards");
            }
        }

        org.springframework.web.cors.CorsConfiguration cors = new org.springframework.web.cors.CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setExposedHeaders(List.of(TraceIdFilter.HEADER_NAME));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", cors);
        return source;
    }

    @Bean
    CorsFilter corsFilter(UrlBasedCorsConfigurationSource source, ObjectMapper objectMapper) {
        CorsFilter filter = new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response) throws IOException {
                response.setStatusCode(ApiError.FORBIDDEN.httpStatus());
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                String traceId = ((ServletServerHttpResponse) response).getServletResponse()
                        .getHeader(TraceIdFilter.HEADER_NAME);
                objectMapper.writeValue(response.getBody(), ApiResponse.failure(ApiError.FORBIDDEN, traceId));
                response.flush();
            }
        });
        return filter;
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsFilter corsFilter) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(corsFilter);
        registration.setEnabled(false);
        return registration;
    }
}
