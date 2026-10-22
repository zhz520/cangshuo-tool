package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.web.JsonBodyBudgetFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;

@Configuration
public class LanzouConfiguration {
    @Bean FilterRegistrationBean<JsonBodyBudgetFilter> lanzouBodyBudget(ObjectMapper mapper) {
        var bean = new FilterRegistrationBean<>(new JsonBodyBudgetFilter(mapper, 8192));
        bean.setName("lanzouBodyBudget");
        bean.addUrlPatterns("/api/v1/web-tools/lanzou_direct/*");
        bean.setOrder(-70);
        return bean;
    }
}
