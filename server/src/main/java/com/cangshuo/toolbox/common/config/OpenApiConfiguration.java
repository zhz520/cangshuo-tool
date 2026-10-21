package com.cangshuo.toolbox.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.tags.Tag;
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
import java.util.TreeMap;
import java.util.ArrayList;
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
                .contact(new Contact().name("沧烁工具箱项目").url("https://github.com/zhz520/cangshuo-tool"))
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
            if (operation.getDescription() == null || operation.getDescription().isBlank()) {
                String identity = anonymous ? "匿名可访问；携带无效 Token 仍可能返回 401。"
                        : path.startsWith("/api/v1/admin/") ? "需要管理员登录签发的 Bearer Token，用户 Token 无权访问。"
                        : "需要已登录账号的 Bearer Token。";
                operation.setDescription(identity + "\n\nJSON 成功响应 code=0；错误响应 data=null，使用 HTTP 状态、业务码及 X-Trace-Id 定位失败。"
                        + "参数范围和请求/响应字段见下方模型。接口业务约定见仓库 docs/API.md。 ");
            }
        }));
    }

    @Bean
    OpenApiCustomizer commonResponseContract() {
        return openApi -> {
            var tags = new TreeMap<String, Tag>(String.CASE_INSENSITIVE_ORDER);
            if (openApi.getTags() != null) openApi.getTags().forEach(tag -> tags.put(tag.getName(), tag));
            Schema<?> nullData = new Schema<>();
            nullData.setTypes(Set.of("null"));
            openApi.getComponents().addSchemas("ApiFailure", new ObjectSchema()
                    .addProperty("code", new IntegerSchema().description("非零业务错误码").example(10001))
                    .addProperty("message", new StringSchema().example("Invalid request"))
                    .addProperty("data", nullData)
                    .addProperty("traceId", new StringSchema().pattern("^[0-9a-f]{32}$"))
                    .required(List.of("code", "message", "data", "traceId")));
            openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                if (operation.getTags() != null) operation.getTags().forEach(name -> tags.computeIfAbsent(name,
                        tagName -> new Tag().name(tagName).description("沧烁工具箱 " + tagName + " 接口")));
                if (operation.getParameters() != null) operation.getParameters().forEach(parameter -> {
                    if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
                        parameter.setDescription(parameterDescription(parameter.getName(), path, parameter.getIn()));
                    }
                });
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
            openApi.setTags(new ArrayList<>(tags.values()));
        };
    }

    private static String parameterDescription(String name, String path, String location) {
        return switch (name) {
            case "page" -> "页码，从 1 开始；省略时使用 schema 中的默认值。";
            case "pageSize" -> "每页条数；允许范围和默认值见 schema。";
            case "keyword" -> "关键词筛选；省略时不限制关键词。";
            case "categoryCode" -> "工具分类编码，例如 CALC、TEXT、DEV；省略时不限分类。";
            case "code" -> "目标资源的稳定唯一编码，来自对应列表。";
            case "id" -> path.contains("short_video_parse") ? "解析响应返回的临时结果 id；过期后需要重新解析。" : "目标记录的唯一 id，来自对应列表。";
            case "key" -> path.contains("storage") ? "服务端上传响应返回的对象 key，不是任意本地文件路径。" : "白名单配置键，来自配置列表。";
            case "cursor" -> "上次拉取响应返回的不透明游标；首次拉取可省略。";
            case "days" -> "统计窗口天数：7、30 或 90。";
            case "limit" -> "返回条数上限；允许范围和默认值见 schema。";
            case "status" -> "按对应业务资源状态筛选；省略时不限状态。";
            case "enabled" -> "按启用状态筛选；省略时不限。";
            case "module" -> "操作日志的模块编码筛选，使用大写编码。";
            case "result" -> "操作日志的结果编码筛选，使用大写编码。";
            case "expirySeconds" -> "预签名下载链接的有效秒数；实际范围受存储配置约束。";
            case "index" -> "解析结果中对应媒体资源的索引，从 0 开始。";
            case "download" -> "true 表示附件下载，false 表示内联预览。";
            case "Range" -> "可选单段字节范围，例如 bytes=0-1023，用于断点续传。";
            case "selection" -> "逗号分隔的图片资源索引，例如 0,1,2；最多选择 50 项。";
            default -> location + " 参数：" + name + "；校验范围见 schema。";
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
