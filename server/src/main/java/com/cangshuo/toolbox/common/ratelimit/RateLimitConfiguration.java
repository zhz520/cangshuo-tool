package com.cangshuo.toolbox.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {
    @Bean
    @ConditionalOnProperty(name = "toolbox.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties properties,
            RateLimitService limiter, ObjectMapper mapper) {
        var registration = new FilterRegistrationBean<>(new RateLimitFilter(properties, limiter, mapper));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
