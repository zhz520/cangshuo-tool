package com.cangshuo.toolbox.common.config;

import com.cangshuo.toolbox.admin.config.AdminBootstrap;
import com.cangshuo.toolbox.auth.service.RefreshTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exports real Springdoc routes/DTOs without local secrets, databases or third-party requests. */
@SpringBootTest(properties = {
        "spring.profiles.active=local", "spring.config.import=",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "spring.flyway.enabled=false", "management.health.redis.enabled=false",
        "toolbox.auth.jwt-secret=1212121212121212121212121212121212121212121212121212121212121212",
        "toolbox.admin.bootstrap-username=", "toolbox.admin.bootstrap-password=",
        "toolbox.storage.enabled=false", "toolbox.ai.enabled=false", "toolbox.media.enabled=false",
        "toolbox.zepp.enabled=false", "toolbox.cors.allowed-origins=",
        "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
class OpenApiExportTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RequestMappingHandlerMapping requestMappingHandlerMapping;
    @Autowired SecurityFilterChain securityFilterChain;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean NamedParameterJdbcTemplate namedJdbc;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean AdminBootstrap bootstrap;
    @MockitoBean RefreshTokenService sessions;

    @Test void exportEveryImplementedBusinessRouteAndResolveReferences() throws Exception {
        byte[] content = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode document = mapper.readTree(content);
        assertEquals("3.1.0", document.path("openapi").asText());
        assertEquals("https://toolapi.zhzgo.cn", document.path("servers").get(0).path("url").asText());
        assertEquals("bearer", document.at("/components/securitySchemes/bearerAuth/scheme").asText());

        Set<String> actual = new HashSet<>();
        requestMappingHandlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
            mapping.getPatternValues().stream().filter(path -> path.startsWith("/api/v1/")).forEach(path ->
                    mapping.getMethodsCondition().getMethods().forEach(method -> actual.add(method.name().toLowerCase() + " " + path)));
        });
        Set<String> exported = new HashSet<>();
        Set<String> operationIds = new HashSet<>();
        var authorization = securityFilterChain.getFilters().stream().filter(AuthorizationFilter.class::isInstance)
                .map(AuthorizationFilter.class::cast).findFirst().orElseThrow().getAuthorizationManager();
        var anonymous = new AnonymousAuthenticationToken("openapi-export", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (!Set.of("get", "post", "put", "patch", "delete", "head", "options").contains(method.getKey())) return;
            exported.add(method.getKey() + " " + path.getKey());
            String id = method.getValue().path("operationId").asText();
            assertFalse(id.isBlank());
            assertTrue(operationIds.add(id), "Duplicate operationId: " + id);
            assertFalse(method.getValue().path("summary").asText().isBlank(), "Missing summary: " + id);
            String samplePath = path.getKey().replaceAll("\\{[^}]+}", "1");
            var request = new MockHttpServletRequest(method.getKey().toUpperCase(Locale.ROOT), samplePath);
            request.setServletPath(samplePath);
            boolean anonymousAllowed = authorization.authorize(() -> anonymous, request).isGranted();
            assertEquals(anonymousAllowed, method.getValue().path("security").isEmpty(), "Documentation/security mismatch: " + id);
        }));
        assertFalse(actual.isEmpty());
        assertEquals(actual, exported, "OpenAPI must include every implemented business route and no planned route");
        assertTrue(document.at("/paths/~1api~1v1~1auth~1login/post/security").isEmpty());
        assertEquals("bearerAuth", document.at("/paths/~1api~1v1~1auth~1me/get/security/0").fieldNames().next());
        assertEquals("bearerAuth", document.at("/paths/~1api~1v1~1admin~1tools/get/security/0").fieldNames().next());
        assertTrue(document.at("/components/schemas/ZeppStepsCapabilities/properties").has("maxSteps"));
        assertFalse(document.at("/components/schemas/ZeppStepsCapabilities/properties").has("platforms"));
        assertTrue(document.at("/components/schemas/MediaCapabilities/properties").has("platforms"));
        assertFalse(document.at("/components/schemas/MediaCapabilities/properties").has("maxSteps"));
        assertTrue(document.at("/components/schemas/ZeppStepsResponse/properties").has("wechatVerified"));
        assertEquals("binary", document.at("/paths/~1api~1v1~1web-tools~1short_video_parse~1assets~1{id}~1{index}/get/responses/206/content/application~1octet-stream/schema/format").asText());
        assertEquals("binary", document.at("/paths/~1api~1v1~1web-tools~1short_video_parse~1albums~1{id}/get/responses/200/content/application~1zip/schema/format").asText());
        assertReferences(document, document);
        verifyNoInteractions(jdbc, namedJdbc, redis, sessions);

        Path output = Path.of(System.getProperty("toolbox.openapi.output", "target/openapi.json")).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        var ordered = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        Files.writeString(output, ordered.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.convertValue(document, Object.class)) + "\n");
        System.out.printf("OpenAPI export: %d operations, %d paths, %d schemas -> %s%n", actual.size(),
                document.path("paths").size(), document.at("/components/schemas").size(), output);
    }

    private void assertReferences(JsonNode root, JsonNode node) {
        if (node.isObject() && node.has("$ref")) {
            String ref = node.path("$ref").asText();
            assertTrue(ref.startsWith("#/"), "External references are not allowed: " + ref);
            assertFalse(root.at(ref.substring(1)).isMissingNode(), "Unresolved reference: " + ref);
        }
        if (node.isContainerNode()) node.elements().forEachRemaining(child -> assertReferences(root, child));
    }
}
