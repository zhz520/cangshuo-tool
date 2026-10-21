package com.cangshuo.toolbox.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI toolboxOpenApi() {
        return new OpenAPI().info(new Info()
                .title("沧烁工具箱 API")
                .version("0.1.0")
                .description("沧烁工具箱已实现的 REST 接口：账号、工具目录与排行、云同步、首页、反馈、网站导航、会员、版本、AI、网页工具及管理后台。"
                        + "JSON 接口统一返回 code/message/data/traceId；code=0 表示成功，错误时 data=null。"
                        + "媒体文件与图集下载返回二进制流。用户和管理员分别使用各自登录接口签发的 Bearer JWT，不能混用。"
                        + "本文件由实际 Controller 和 DTO 生成，不包含路线图中尚未实现的接口。"))
                .servers(List.of(new Server().url("https://toolapi.zhzgo.cn").description("生产 API"),
                        new Server().url("http://127.0.0.1:8080").description("本地开发")))
                .schemaRequirement("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT")
                        .description("用户 Token 来自 POST /api/v1/auth/login；管理员 Token 来自 POST /api/v1/admin/auth/login。"));
    }

    @Bean
    OperationCustomizer stableOperationIds() {
        return (operation, handler) -> {
            var annotation = handler.getMethodAnnotation(io.swagger.v3.oas.annotations.Operation.class);
            if (annotation == null || annotation.operationId().isBlank()) {
                // Springdoc's numeric collision suffixes depend on scan order; use a stable controller/method ID.
                operation.setOperationId(handler.getBeanType().getSimpleName().replace("Controller", "")
                        + "_" + handler.getMethod().getName());
            }
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer documentedAuthentication() {
        return openApi -> openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            String verb = method.name();
            boolean anonymous = path.equals("/api/v1/health")
                    || (verb.equals("GET") && Set.of("/api/v1/tools", "/api/v1/tools/popular", "/api/v1/app/version",
                            "/api/v1/site-links", "/api/v1/home/recommendations", "/api/v1/home/announcements").contains(path))
                    || (verb.equals("POST") && Set.of("/api/v1/auth/register", "/api/v1/auth/login",
                            "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/admin/auth/login").contains(path))
                    || (verb.equals("POST") && path.equals("/api/v1/tools/{code}/usage"))
                    || (verb.equals("GET") && Set.of("/api/v1/web-tools/zepp_steps/capabilities",
                            "/api/v1/web-tools/short_video_parse/capabilities",
                            "/api/v1/web-tools/short_video_parse/assets/{id}/{index}",
                            "/api/v1/web-tools/short_video_parse/albums/{id}").contains(path))
                    || (verb.equals("POST") && Set.of("/api/v1/web-tools/zepp_steps/submit",
                            "/api/v1/web-tools/short_video_parse/parse").contains(path))
                    || (verb.equals("DELETE") && path.equals("/api/v1/web-tools/short_video_parse/results/{id}"));
            operation.setSecurity(anonymous ? List.of() : List.of(new SecurityRequirement().addList("bearerAuth")));
        }));
    }

    @Bean
    OpenApiCustomizer commonResponseContract() {
        return openApi -> {
            Schema<?> nullData = new Schema<>();
            nullData.setTypes(Set.of("null"));
            openApi.getComponents().addSchemas("ApiFailure", new ObjectSchema()
                    .addProperty("code", new IntegerSchema().description("非零业务错误码").example(10001))
                    .addProperty("message", new StringSchema().example("Invalid request"))
                    .addProperty("data", nullData)
                    .addProperty("traceId", new StringSchema().pattern("^[0-9a-f]{32}$"))
                    .required(List.of("code", "message", "data", "traceId")));
            openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                Map<String, String> errors = Map.of("400", "10001：请求参数无效", "401", "10002：Token 缺失、无效或过期；匿名接口携带无效 Token 也可能返回此状态",
                        "403", "10005：权限或跨域来源不允许", "500", "10000：服务内部错误", "503", "10008：服务暂不可用");
                errors.forEach((status, description) -> operation.getResponses().putIfAbsent(status, failure(description)));
                if (!path.equals("/api/v1/health")) {
                    operation.getResponses().putIfAbsent("429", failure("10007：请求过于频繁；按 Retry-After 等待后重试")
                            .addHeaderObject("Retry-After", new Header().schema(new IntegerSchema()).description("重试等待秒数")));
                }
                operation.getResponses().values().forEach(response -> response.addHeaderObject("X-Trace-Id",
                        new Header().description("服务端生成的请求编号").schema(new StringSchema().pattern("^[0-9a-f]{32}$"))));
            }));
        };
    }

    private static ApiResponse failure(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiFailure"))));
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
