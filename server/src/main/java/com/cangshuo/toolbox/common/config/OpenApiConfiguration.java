package com.cangshuo.toolbox.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI toolboxOpenApi() {
        return new OpenAPI().info(new Info()
                .title("沧烁工具箱 API")
                .version("0.1.0")
                .description("当前提供服务健康检查；业务接口按路线图逐步实现。"));
    }

    @Bean
    OpenApiCustomizer nullableResponseData() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((name, rawSchema) -> {
                Schema<?> responseSchema = rawSchema;
                var properties = responseSchema.getProperties();
                if (name.startsWith("ApiResponse") && properties != null && properties.containsKey("data")) {
                    // Error envelopes use data=null even when the success DTO is a concrete type.
                    Schema<?> nullSchema = new Schema<>();
                    nullSchema.setTypes(Set.of("null"));
                    properties.put("data", new ComposedSchema()
                            .addAnyOfItem(properties.get("data"))
                            .addAnyOfItem(nullSchema));
                }
            });
        };
    }
}
