package com.dbaas.util;

import com.dbaas.exception.EncryptionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for encrypting/decrypting sensitive data.
 * Uses AES-256-GCM for authenticated encryption.
 */
@Component
@Slf4j
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // bits

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypt a plaintext string.
     *
     * @param plaintext The text to encrypt
     * @param key       Base64-encoded 256-bit key
     * @return Base64-encoded ciphertext (IV prepended)
     */
    public String encrypt(String plaintext, String key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(key);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, paramSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new EncryptionException("Encryption", e);
        }
    }

    /**
     * Decrypt a ciphertext string.
     *
     * @param ciphertext Base64-encoded ciphertext (IV prepended)
     * @param key        Base64-encoded 256-bit key
     * @return Decrypted plaintext
     */
    public String decrypt(String ciphertext, String key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(key);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            // Extract ciphertext
            byte[] encryptedBytes = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, paramSpec);

            byte[] plaintext = cipher.doFinal(encryptedBytes);
            return new String(plaintext);

        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new EncryptionException("Decryption", e);
        }
    }

    /**
     * Generate a new 256-bit encryption key.
     *
     * @return Base64-encoded key
     */
    public String generateKey() {
        byte[] key = new byte[32]; // 256 bits
        secureRandom.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * Hash a password for cluster (not for user auth - use BCrypt for that).
     * This is a simple hash for generating unique passwords per cluster.
     *
     * @param clusterId Cluster identifier
     * @param salt      Salt value
     * @return Hashed password
     */
    public String hashClusterPassword(String clusterId, String salt) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            String input = clusterId + ":" + salt;
            byte[] hash = md.digest(input.getBytes());

            // Take first 16 bytes and encode as base64 for readable password
            byte[] shortened = new byte[16];
            System.arraycopy(hash, 0, shortened, 0, 16);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(shortened);

        } catch (Exception e) {
            throw new EncryptionException("Password hashing", e);
        }
    }
}
