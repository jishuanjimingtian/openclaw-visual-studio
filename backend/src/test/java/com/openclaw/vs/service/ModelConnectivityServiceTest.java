package com.openclaw.vs.service;

import com.openclaw.vs.dto.ModelTestResult;
import com.openclaw.vs.model.ModelConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelConnectivityServiceTest {

    @Mock
    private OpenClawModelConfigService openClawModelConfigService;

    @InjectMocks
    private ModelConnectivityService connectivityService;

    @Test
    void test_disabledModel() {
        ModelConfig model = ModelConfig.builder()
            .id("1")
            .name("X")
            .provider("qwen")
            .endpoint("qwen/qwen3.5-plus")
            .enabled(false)
            .build();

        ModelTestResult result = connectivityService.test(model, "sk-test");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("禁用");
    }

    @Test
    void test_noApiKey() {
        ModelConfig model = ModelConfig.builder()
            .id("1")
            .name("X")
            .provider("qwen")
            .endpoint("qwen/qwen3.5-plus")
            .enabled(true)
            .build();

        when(openClawModelConfigService.hasProviderApiKey(anyString())).thenReturn(false);

        ModelTestResult result = connectivityService.test(model, null);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("未配置 API Key");
    }
}
