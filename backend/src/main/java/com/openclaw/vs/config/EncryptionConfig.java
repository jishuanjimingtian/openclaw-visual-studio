package com.openclaw.vs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class EncryptionConfig {

    @Value("${app.security.api-key-encryption-key:openclaw-vs-dev-key}")
    private String encryptionKey;

    @Bean
    public TextEncryptor textEncryptor() {
        return Encryptors.text(toHexPassword(encryptionKey), "deadbeefcafebabe");
    }

    /**
     * Spring Encryptors.text 要求 password 为十六进制字符串；将任意配置串稳定映射为 32 位 hex。
     */
    static String toHexPassword(String raw) {
        if (raw != null && raw.matches("^[0-9a-fA-F]{32,}$")) {
            return raw.substring(0, 32);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((raw != null ? raw : "openclaw-vs-default").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化 API Key 加密器", e);
        }
    }
}
