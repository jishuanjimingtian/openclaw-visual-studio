package com.openclaw.vs.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelServiceMaskApiKeyTest {

    @Test
    void maskApiKey_showsPrefixAndSuffix() {
        assertThat(ModelService.maskApiKey("sk-45ed6346854e4a0ebf53158724f1a08f"))
            .isEqualTo("sk-45ed••••a08f");
    }

    @Test
    void maskApiKey_shortKey() {
        assertThat(ModelService.maskApiKey("short")).isEqualTo("********");
    }
}
