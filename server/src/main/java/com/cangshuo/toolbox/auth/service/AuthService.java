package com.cangshuo.toolbox.auth.service;

import com.cangshuo.toolbox.auth.model.AuthResponse;
import com.cangshuo.toolbox.auth.model.LoginRequest;
import com.cangshuo.toolbox.auth.model.RegisterRequest;
import com.cangshuo.toolbox.auth.model.UserResponse;
import com.cangshuo.toolbox.auth.repository.UserAccountRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenService tokens;
    private final String dummyHash;

    @Transactional
    public void deleteAccount(String subject, com.cangshuo.toolbox.auth.model.DeleteAccountRequest request) {
        var profile = currentUser(subject);
        AuthInput.password(request.password());
        var account = users.findById(profile.id()).filter(value -> value.status() == 1)
            .orElseThrow(() -> new ApiException(ApiError.UNAUTHENTICATED));
        if (!account.email().equals(AuthInput.email(request.email())) || !passwords.matches(request.password(), account.passwordHash()))
            throw new ApiException(ApiError.INVALID_CREDENTIALS);
        if (!users.delete(account.id(), account.passwordHash())) throw new ApiException(ApiError.UNAUTHENTICATED);
    }

    @Transactional
    public UserResponse updateProfile(String subject, com.cangshuo.toolbox.auth.model.ProfileRequest request) {
        var current = currentUser(subject);
        var nickname = AuthInput.nickname(request.nickname());
        if (!users.updateNickname(current.id(), nickname)) throw new ApiException(ApiError.UNAUTHENTICATED);
        return new UserResponse(current.id(), current.email(), nickname);
    }

    public AuthService(UserAccountRepository users, PasswordEncoder passwords, RefreshTokenService tokens) {
        this.users = users; this.passwords = passwords; this.tokens = tokens;
        dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = AuthInput.email(request.email());
        AuthInput.password(request.password());
        String nickname = AuthInput.nickname(request.nickname());
        String hash = passwords.encode(request.password());
        try {
            return tokens.start(users.create(email, hash, nickname));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(ApiError.ACCOUNT_EXISTS);
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = AuthInput.email(request.email());
        AuthInput.password(request.password());
        var account = users.findByEmail(email);
        boolean matches = passwords.matches(request.password(), account.map(a -> a.passwordHash()).orElse(dummyHash));
        if (account.isEmpty() || !matches || account.get().status() != 1) {
            throw new ApiException(ApiError.INVALID_CREDENTIALS);
        }
        users.recordLogin(account.get().id());
        return tokens.start(account.get());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String subject) {
        final long id;
        try { id = Long.parseLong(subject); } catch (NumberFormatException exception) {
            throw new ApiException(ApiError.UNAUTHENTICATED);
        }
        return users.findById(id).filter(account -> account.status() == 1)
                .orElseThrow(() -> new ApiException(ApiError.UNAUTHENTICATED)).publicProfile();
    }
}
