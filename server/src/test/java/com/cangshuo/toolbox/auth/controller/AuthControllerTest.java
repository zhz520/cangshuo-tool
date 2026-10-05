package com.cangshuo.toolbox.auth.controller;

import com.cangshuo.toolbox.auth.config.AuthConfiguration;
import com.cangshuo.toolbox.auth.model.*;
import com.cangshuo.toolbox.auth.service.AccessTokenService;
import com.cangshuo.toolbox.auth.service.AuthService;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.security.ApiSecurityErrorHandler;
import com.cangshuo.toolbox.common.security.CorsConfiguration;
import com.cangshuo.toolbox.common.security.SecurityConfiguration;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, properties = {
        "toolbox.auth.jwt-secret=1212121212121212121212121212121212121212121212121212121212121212",
        "toolbox.cors.allowed-origins="})
@Import({SecurityConfiguration.class, ApiSecurityErrorHandler.class, CorsConfiguration.class,
        AuthConfiguration.class, TraceIdFilter.class, com.cangshuo.toolbox.auth.config.SessionJwtConverter.class})
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired Clock clock;
    @MockitoBean AuthService service;
    @MockitoBean com.cangshuo.toolbox.auth.service.RefreshTokenService sessions;

    @Test void anonymousRegisterReturns201AndEnvelope() throws Exception {
        when(service.register(any())).thenReturn(new AccessTokenService(encoder, clock)
                .issue(new UserAccount(1, "a@b.com", "hash", "user", 1), "12".repeat(16), "12".repeat(16) + "." + "a".repeat(43), clock.instant().plusSeconds(2592000)));
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"email\":\"a@b.com\",\"password\":\"password12\",\"nickname\":\"user\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andExpect(header().exists("X-Trace-Id"));
    }
    @Test void invalidBodyUsesExistingErrorEnvelope() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"a@b.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(service);
    }
    @Test void duplicateAndWrongCredentialsAreStablePublicErrors() throws Exception {
        when(service.register(any())).thenThrow(new ApiException(ApiError.ACCOUNT_EXISTS));
        when(service.login(any())).thenThrow(new ApiException(ApiError.INVALID_CREDENTIALS));
        String body = "{\"email\":\"a@b.com\",\"password\":\"password12\",\"nickname\":\"user\"}";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(20002));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(20001));
    }
    @Test void meRequiresValidBearerAndPassesOnlySubject() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(10002));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(10002));
        when(sessions.active(any(), any())).thenReturn(true);
        var response = new AccessTokenService(encoder, clock).issue(new UserAccount(7, "a@b.com", "hash", "user", 1), "12".repeat(16), "12".repeat(16) + "." + "a".repeat(43), clock.instant().plusSeconds(2592000));
        when(service.currentUser("7")).thenReturn(new UserResponse(7, "a@b.com", "user"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + response.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(7));
        verify(service).currentUser("7");
    }
    @Test void authPrefixAndOtherMethodsAreNotAnonymous() throws Exception {
        mvc.perform(get("/api/v1/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/other")).andExpect(status().isUnauthorized());
    }
    @Test void profileUpdateUsesBearerSubjectAndValidatesBody() throws Exception {
        mvc.perform(put("/api/v1/auth/me").contentType("application/json").content("{\"nickname\":\"new\"}"))
                .andExpect(status().isUnauthorized());
        when(sessions.active(any(), any())).thenReturn(true);
        var response = new AccessTokenService(encoder, clock).issue(new UserAccount(7, "a@b.com", "hash", "old", 1),
                "12".repeat(16), "12".repeat(16) + "." + "a".repeat(43), clock.instant().plusSeconds(2592000));
        when(service.updateProfile(eq("7"), any())).thenReturn(new UserResponse(7,"a@b.com","new"));
        mvc.perform(put("/api/v1/auth/me").header("Authorization", "Bearer " + response.accessToken())
                .contentType("application/json").content("{\"nickname\":\"new\",\"id\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(7));
        verify(service).updateProfile(eq("7"), any());
        mvc.perform(put("/api/v1/auth/me").header("Authorization", "Bearer " + response.accessToken())
                .contentType("application/json").content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void accountDeletionRequiresBearerAndConfirmedBody() throws Exception {
        mvc.perform(delete("/api/v1/auth/me").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        when(sessions.active(any(),any())).thenReturn(true);
        var token=new AccessTokenService(encoder,clock).issue(new UserAccount(7,"a@b.com","hash","u",1),
            "12".repeat(16),"12".repeat(16)+"."+"a".repeat(43),clock.instant().plusSeconds(2592000));
        mvc.perform(delete("/api/v1/auth/me").header("Authorization","Bearer "+token.accessToken())
            .contentType("application/json").content("{\"email\":\"a@b.com\",\"password\":\"password12\",\"id\":999}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").isEmpty());
        verify(service).deleteAccount(eq("7"),any());
        mvc.perform(delete("/api/v1/auth/me").header("Authorization","Bearer "+token.accessToken())
            .contentType("application/json").content("{}")) .andExpect(status().isBadRequest());
    }
}
