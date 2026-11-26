package com.gis.warplan.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.gis.warplan.config.WarplanProperties;
import com.gis.warplan.web.BadRequestException;

/**
 * AES-GCM helpers for at-rest secrets (GeoServer REST password). Key is derived from the JWT secret.
 */
@Service
public class SecretCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCryptoService(WarplanProperties properties) {
        this.key = deriveKey(properties.getJwt().getSecret());
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new BadRequestException("Could not encrypt the secret.");
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            if (raw.length <= IV_LENGTH) {
                throw new BadRequestException("Stored secret is invalid.");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            System.arraycopy(raw, IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Could not decrypt the stored secret.");
        }
    }

    private static SecretKey deriveKey(String secret) {
        try {
            String material = secret == null || secret.isBlank() ? "warplan-default-secret" : secret;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Could not derive encryption key", ex);
        }
    }
}
