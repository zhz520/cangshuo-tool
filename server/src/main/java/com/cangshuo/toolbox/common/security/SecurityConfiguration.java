package com.cangshuo.toolbox.common.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment,
                                          ApiSecurityErrorHandler errorHandler,
                                          com.cangshuo.toolbox.auth.config.CombinedJwtConverter combinedConverter) throws Exception {
        boolean localDocumentation = environment.acceptsProfiles(Profiles.of("local"))
                && environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false);
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(combinedConverter))
                        .authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(requests -> {
                    requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    requests.requestMatchers("/api/v1/health", "/actuator/health").permitAll();
                    requests.requestMatchers(HttpMethod.GET, "/api/v1/tools").permitAll();
                    requests.requestMatchers(HttpMethod.GET, "/api/v1/home/recommendations").permitAll();
                    requests.requestMatchers(HttpMethod.GET, "/api/v1/home/announcements").permitAll();
                    requests.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                            "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll();
                    requests.requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll();
                    if (localDocumentation) {
                        requests.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                                .permitAll();
                    }
                    requests.requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN");
                    requests.anyRequest().authenticated();
                });
        return http.build();
    }
}
