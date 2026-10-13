package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.web.JsonBodyBudgetFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;

@Configuration
public class MediaConfiguration {
    @Bean FilterRegistrationBean<JsonBodyBudgetFilter> mediaBodyBudget(ObjectMapper mapper) {
        var bean = new FilterRegistrationBean<>(new JsonBodyBudgetFilter(mapper, 16384));
        bean.setName("shortVideoBodyBudget");
        bean.addUrlPatterns("/api/v1/web-tools/short_video_parse/*");
        bean.setOrder(-70);
        return bean;
    }
}
