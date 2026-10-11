package com.cangshuo.toolbox.statistics.web;

import com.cangshuo.toolbox.statistics.repository.StatisticsRepository;
import com.cangshuo.toolbox.statistics.service.ApiTrafficMeter;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StatisticsConfiguration {
    @Bean FilterRegistrationBean<ApiTrafficFilter> apiTrafficFilter(ApiTrafficMeter meter) {
        var registration = new FilterRegistrationBean<>(new ApiTrafficFilter(meter));
        // Wrap Spring Security, including its 401/403 responses; TraceIdFilter stays outermost.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
    @Bean WebMvcConfigurer activityConfigurer(StatisticsRepository repository, ApiTrafficMeter meter, Clock clock) {
        var activity = new UserActivityInterceptor(repository, meter, clock);
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                registry.addInterceptor(activity).addPathPatterns("/api/v1/**");
            }
        };
    }
}
