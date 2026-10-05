package com.cangshuo.toolbox.auth.service;

import com.cangshuo.toolbox.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AuthInputTest {
    @Test void normalizesEmailAndNicknameButNotPassword() {
        assertEquals("name+tag@example.com", AuthInput.email(" Name+Tag@Example.COM "));
        assertEquals("沧烁", AuthInput.nickname(" 沧烁 "));
        assertDoesNotThrow(() -> AuthInput.password("  secret  "));
    }
    @ParameterizedTest @ValueSource(strings = {"", "x", "a@localhost", "a..b@example.com", "a@-example.com", "你@example.com", "a@b.com\nother"})
    void rejectsInvalidEmail(String value) { assertThrows(ApiException.class, () -> AuthInput.email(value)); }
    @Test void enforcesBcryptUtf8LimitWithoutTruncation() {
        assertDoesNotThrow(() -> AuthInput.password("密".repeat(24)));
        assertThrows(ApiException.class, () -> AuthInput.password("密".repeat(25)));
        assertThrows(ApiException.class, () -> AuthInput.password("a".repeat(73)));
    }
    @Test void rejectsControlsAndMalformedUnicode() {
        assertThrows(ApiException.class, () -> AuthInput.password("secret12\u0000"));
        assertThrows(ApiException.class, () -> AuthInput.password("secret12\uD800"));
        assertThrows(ApiException.class, () -> AuthInput.nickname("\uDC00"));
        assertThrows(ApiException.class, () -> AuthInput.nickname("   "));
    }
}
