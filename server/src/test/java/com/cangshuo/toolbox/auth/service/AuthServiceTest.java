package com.cangshuo.toolbox.auth.service;

import com.cangshuo.toolbox.auth.model.*;
import com.cangshuo.toolbox.auth.repository.UserAccountRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final RefreshTokenService tokens = mock(RefreshTokenService.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private AuthService service;
    @BeforeEach void setup() { service = new AuthService(users, passwords, tokens); }

    @Test void registersNormalizedAccountWithSaltedHash() {
        when(users.create(eq("user@example.com"), anyString(), eq("用户"))).thenAnswer(call -> {
            String hash = call.getArgument(1);
            assertNotEquals("password12", hash);
            assertTrue(passwords.matches("password12", hash));
            return new UserAccount(1, call.getArgument(0), hash, call.getArgument(2), 1);
        });
        service.register(new RegisterRequest(" USER@example.com ", "password12", " 用户 "));
        verify(tokens).start(any(UserAccount.class));
    }
    @Test void duplicateInsertMapsToStableConflict() {
        when(users.create(anyString(), anyString(), anyString())).thenThrow(new DuplicateKeyException("redacted"));
        assertEquals(ApiError.ACCOUNT_EXISTS, assertThrows(ApiException.class,
                () -> service.register(new RegisterRequest("user@example.com", "password12", "user"))).error());
        verifyNoInteractions(tokens);
    }
    @Test void validLoginUpdatesTimestampAndIssuesToken() {
        var account = new UserAccount(7, "user@example.com", passwords.encode("password12"), "user", 1);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(account));
        service.login(new LoginRequest("USER@example.com", "password12"));
        verify(users).recordLogin(7);
        verify(tokens).start(account);
    }
    @Test void unknownWrongAndDisabledAccountsUseSameFailure() {
        var request = new LoginRequest("user@example.com", "password12");
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        assertEquals(ApiError.INVALID_CREDENTIALS, assertThrows(ApiException.class, () -> service.login(request)).error());
        when(users.findByEmail(anyString())).thenReturn(Optional.of(new UserAccount(1, request.email(), passwords.encode("different"), "u", 1)));
        assertEquals(ApiError.INVALID_CREDENTIALS, assertThrows(ApiException.class, () -> service.login(request)).error());
        when(users.findByEmail(anyString())).thenReturn(Optional.of(new UserAccount(1, request.email(), passwords.encode(request.password()), "u", 0)));
        assertEquals(ApiError.INVALID_CREDENTIALS, assertThrows(ApiException.class, () -> service.login(request)).error());
        verifyNoInteractions(tokens);
        verify(users, never()).recordLogin(anyLong());
    }
    @Test void meRejectsDeletedOrDisabledUser() {
        when(users.findById(1)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.currentUser("1"));
        when(users.findById(1)).thenReturn(Optional.of(new UserAccount(1, "a@b.com", "redacted", "u", 0)));
        assertThrows(ApiException.class, () -> service.currentUser("1"));
    }
    @Test void sensitiveRecordsAreRedacted() {
        assertFalse(new LoginRequest("user@example.com", "secret12").toString().contains("secret12"));
        assertFalse(new RegisterRequest("user@example.com", "secret12", "u").toString().contains("secret12"));
        assertFalse(new UserAccount(1, "a@b.com", "hash", "u", 1).toString().contains("hash"));
    }
    @Test void profileOnlyUpdatesTheAuthenticatedAccountAndNormalizesNickname() {
        when(users.findById(7)).thenReturn(Optional.of(new UserAccount(7, "a@b.com", "hash", "old", 1)));
        when(users.updateNickname(7, "新昵称")).thenReturn(true);
        assertEquals(new UserResponse(7, "a@b.com", "新昵称"), service.updateProfile("7", new ProfileRequest(" 新昵称 ")));
        verify(users).updateNickname(7, "新昵称");
        assertThrows(ApiException.class, () -> service.updateProfile("7", new ProfileRequest("bad\nname")));
    }
    @Test void disabledOrDisappearingAccountCannotUpdateProfile() {
        when(users.findById(7)).thenReturn(Optional.of(new UserAccount(7, "a@b.com", "hash", "old", 0)));
        assertThrows(ApiException.class, () -> service.updateProfile("7", new ProfileRequest("new")));
        verify(users, never()).updateNickname(anyLong(), anyString());
        when(users.findById(7)).thenReturn(Optional.of(new UserAccount(7, "a@b.com", "hash", "old", 1)));
        assertThrows(ApiException.class, () -> service.updateProfile("7", new ProfileRequest("new")));
    }
    @Test void deletionRequiresMatchingEmailAndPasswordAndOnlyDeletesSubject() {
        var user=new UserAccount(7,"a@b.com",passwords.encode("password12"),"u",1);
        when(users.findById(7)).thenReturn(Optional.of(user)); when(users.delete(7,user.passwordHash())).thenReturn(true);
        assertThrows(ApiException.class,()->service.deleteAccount("7",new DeleteAccountRequest("other@b.com","password12")));
        assertThrows(ApiException.class,()->service.deleteAccount("7",new DeleteAccountRequest("a@b.com","wrongpass")));
        verify(users,never()).delete(anyLong(),anyString());
        service.deleteAccount("7",new DeleteAccountRequest(" A@B.COM ","password12"));
        verify(users).delete(7,user.passwordHash()); verifyNoInteractions(tokens);
    }
    @Test void deletionFailsForMissingDisabledOrChangedAccount() {
        assertThrows(ApiException.class,()->service.deleteAccount("7",new DeleteAccountRequest("a@b.com","password12")));
        var user=new UserAccount(7,"a@b.com",passwords.encode("password12"),"u",0);
        when(users.findById(7)).thenReturn(Optional.of(user));
        assertThrows(ApiException.class,()->service.deleteAccount("7",new DeleteAccountRequest("a@b.com","password12")));
        when(users.findById(7)).thenReturn(Optional.of(new UserAccount(7,"a@b.com",user.passwordHash(),"u",1)));
        assertThrows(ApiException.class,()->service.deleteAccount("7",new DeleteAccountRequest("a@b.com","password12")));
        assertEquals("DeleteAccountRequest[redacted]",new DeleteAccountRequest("a@b.com","secret12").toString());
    }
}
