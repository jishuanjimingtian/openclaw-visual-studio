package com.openclaw.vs.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptionConfigTest {

    @Test
    void textEncryptor_acceptsArbitraryPassword() {
        String hex = EncryptionConfig.toHexPassword("change-me-in-production");
        assertThat(hex).matches("^[0-9a-f]{32}$");

        TextEncryptor encryptor = org.springframework.security.crypto.encrypt.Encryptors.text(hex, "deadbeefcafebabe");
        String cipher = encryptor.encrypt("sk-test-key");
        assertThat(encryptor.decrypt(cipher)).isEqualTo("sk-test-key");
    }
}
