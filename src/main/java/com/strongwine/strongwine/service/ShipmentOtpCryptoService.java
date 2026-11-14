package com.strongwine.strongwine.service;

import com.strongwine.strongwine.exception.StrongWineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

@Service
public class ShipmentOtpCryptoService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentOtpCryptoService.class);
    private static final String PREFIX_V1 = "v1:";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String LOCAL_DEV_FALLBACK_SEED = "strongwine-v3-dev-otp-fallback-key-configure-env-in-prod";

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ShipmentOtpCryptoService() {
        this("");
    }

    @Autowired
    public ShipmentOtpCryptoService(@Value("${app.otp.encryption-key:}") String encryptionKey) {
        String effectiveSeed;
        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.warn("app.otp.encryption-key is not configured; using deterministic local development OTP encryption key.");
            effectiveSeed = LOCAL_DEV_FALLBACK_SEED;
        } else {
            effectiveSeed = encryptionKey.trim();
        }
        this.secretKey = deriveAes256Key(effectiveSeed);
    }

    public String encrypt(String plainOtp) {
        if (plainOtp == null || plainOtp.isBlank()) {
            throw new IllegalArgumentException("OTP value must not be null or blank");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainOtp.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX_V1 + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new StrongWineException("Failed to encrypt shipment OTP", ex);
        }
    }

    public Optional<String> decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank() || !cipherText.startsWith(PREFIX_V1)) {
            return Optional.empty();
        }
        try {
            String encodedPayload = cipherText.substring(PREFIX_V1.length());
            byte[] combined = Base64.getDecoder().decode(encodedPayload);
            if (combined.length <= GCM_IV_LENGTH_BYTES) {
                return Optional.empty();
            }

            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(encrypted);
            return Optional.of(new String(plainBytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            return Optional.empty();
        }
    }

    public String decryptOrThrow(String cipherText) {
        return decrypt(cipherText)
            .orElseThrow(() -> new StrongWineException("Invalid or corrupted shipment OTP payload"));
    }

    private SecretKey deriveAes256Key(String seed) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
