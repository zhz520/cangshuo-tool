package com.cangshuo.toolbox.zepp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(ZeppProperties.class)
public class ZeppConfiguration {
    @Bean FilterRegistrationBean<ZeppRequestBudgetFilter> zeppRequestBudget(ObjectMapper mapper) {
        var bean = new FilterRegistrationBean<>(new ZeppRequestBudgetFilter(mapper));
        bean.addUrlPatterns("/api/v1/web-tools/zepp_steps/submit");
        bean.setOrder(1);
        return bean;
    }
}
