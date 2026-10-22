package com.cangshuo.toolbox.lanzou;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Encrypts the stable redirect capability, including an optional share password. */
final class LanzouLinkTokenCodec {
    private static final byte VERSION = 1;
    private static final byte[] AAD = "lanzou-direct-link-v1".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_BYTES = 12, TAG_BITS = 128, MAX_TOKEN = 4096;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    LanzouLinkTokenCodec(String secret) {
        String value = secret == null ? "" : secret.strip();
        try {
            key = value.getBytes(StandardCharsets.UTF_8).length < 32 ? null : new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    boolean available() { return key != null; }

    String encode(LanzouInput input, Instant createdAt) {
        if (!available()) throw new IllegalStateException("Permanent links are not configured");
        byte[] url = input.uri().toASCIIString().getBytes(StandardCharsets.UTF_8);
        byte[] password = input.password().getBytes(StandardCharsets.UTF_8);
        if (url.length > 4096 || password.length > 256) throw new IllegalArgumentException("Link payload too large");
        ByteBuffer payload = ByteBuffer.allocate(1 + 8 + 2 + url.length + 2 + password.length);
        payload.put(VERSION).putLong(createdAt.getEpochSecond()).putShort((short) url.length).put(url)
                .putShort((short) password.length).put(password);
        byte[] nonce = new byte[NONCE_BYTES]; random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(payload.array());
            byte[] token = ByteBuffer.allocate(1 + NONCE_BYTES + encrypted.length).put(VERSION).put(nonce).put(encrypted).array();
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
            if (encoded.length() > MAX_TOKEN) throw new IllegalArgumentException("Token too large");
            return encoded;
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    Decoded decode(String token) {
        if (!available() || token == null || token.length() < 24 || token.length() > MAX_TOKEN
                || !token.matches("[A-Za-z0-9_-]+")) return null;
        try {
            byte[] raw = Base64.getUrlDecoder().decode(token);
            if (raw.length < 1 + NONCE_BYTES + TAG_BITS / 8 || raw[0] != VERSION) return null;
            byte[] nonce = java.util.Arrays.copyOfRange(raw, 1, 1 + NONCE_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(raw, 1 + NONCE_BYTES, raw.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(AAD);
            ByteBuffer payload = ByteBuffer.wrap(cipher.doFinal(encrypted));
            if (payload.get() != VERSION) return null;
            long epoch = payload.getLong();
            int urlLength = Short.toUnsignedInt(payload.getShort());
            if (urlLength < 1 || urlLength > 4096 || payload.remaining() < urlLength + 2) return null;
            byte[] url = new byte[urlLength]; payload.get(url);
            int passwordLength = Short.toUnsignedInt(payload.getShort());
            if (passwordLength > 256 || payload.remaining() != passwordLength) return null;
            byte[] password = new byte[passwordLength]; payload.get(password);
            String uriText = new String(url, StandardCharsets.UTF_8);
            String passwordText = new String(password, StandardCharsets.UTF_8);
            LanzouInput input = LanzouInput.parse(uriText, passwordText);
            if (!input.uri().toASCIIString().equals(uriText) || !input.password().equals(passwordText)) return null;
            return new Decoded(input, Instant.ofEpochSecond(epoch));
        } catch (Exception invalid) {
            return null;
        }
    }

    record Decoded(LanzouInput input, Instant createdAt) {}
}
