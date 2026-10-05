package com.cangshuo.toolbox.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
    @org.springframework.context.annotation.Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<AiRequestBudgetFilter> aiRequestBudget(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new AiRequestBudgetFilter(mapper));
        registration.addUrlPatterns("/api/v1/tools/ai-text");
        registration.setOrder(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 1);
        return registration;
    }
}
