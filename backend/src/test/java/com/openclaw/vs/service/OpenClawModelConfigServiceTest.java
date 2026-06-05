package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawModelConfigServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void applyModelRegistration_forBuiltInQwen_addsValidProviderModel() throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree("""
            {
              "agents": { "defaults": { "models": {} } },
              "models": { "providers": { "qwen": { "apiKey": "test-key" } } }
            }
            """);

        OpenClawModelConfigService.applyModelRegistration(root, "qwen/qwen3.5-plus");

        assertThat(root.path("agents").path("defaults").path("models").has("qwen/qwen3.5-plus")).isTrue();
        JsonNode providerModels = root.path("models").path("providers").path("qwen").path("models");
        assertThat(providerModels.isArray()).isTrue();
        assertThat(providerModels.get(0).path("id").asText()).isEqualTo("qwen3.5-plus");
        assertThat(providerModels.get(0).path("name").asText()).isEqualTo("qwen3.5-plus");
    }

    @Test
    void applyModelRegistration_repairsIdOnlyBuiltInProviderModels() throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree("""
            {
              "agents": { "defaults": { "models": { "qwen/qwen-plus": {} } } },
              "models": {
                "providers": {
                  "qwen": {
                    "apiKey": "k",
                    "models": [ { "id": "qwen-plus" } ]
                  }
                }
              }
            }
            """);

        OpenClawModelConfigService.applyModelRegistration(root, "qwen/qwen-plus");

        JsonNode providerModels = root.path("models").path("providers").path("qwen").path("models");
        assertThat(providerModels).anyMatch(n ->
            "qwen3.5-plus".equals(n.path("id").asText()) && "qwen3.5-plus".equals(n.path("name").asText()));
        assertThat(providerModels).noneMatch(n -> "qwen-plus".equals(n.path("id").asText()));
    }

    @Test
    void applyModelRegistration_forCustomProvider_addsIdAndName() throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree("""
            {
              "agents": { "defaults": { "models": {} } },
              "models": { "providers": { "myllm": { "baseUrl": "https://api.example.com/v1" } } }
            }
            """);

        OpenClawModelConfigService.applyModelRegistration(root, "myllm/custom-model");

        JsonNode providerModels = root.path("models").path("providers").path("myllm").path("models");
        assertThat(providerModels.get(0).path("id").asText()).isEqualTo("custom-model");
        assertThat(providerModels.get(0).path("name").asText()).isEqualTo("custom-model");
    }

    @Test
    void unregisterModel_removesFromAllowlistAndFallbacks() throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree("""
            {
              "agents": {
                "defaults": {
                  "model": {
                    "primary": "deepseek/deepseek-v4-flash",
                    "fallbacks": ["qwen/glm-4.7", "qwen/qwen3.5-plus"]
                  },
                  "models": {
                    "qwen/glm-4.7": {},
                    "qwen/qwen3.5-plus": {},
                    "deepseek/deepseek-v4-flash": {}
                  }
                }
              },
              "models": {
                "providers": {
                  "qwen": {
                    "apiKey": "k",
                    "models": [
                      { "id": "glm-4.7", "name": "glm-4.7" },
                      { "id": "qwen3.5-plus", "name": "qwen3.5-plus" }
                    ]
                  }
                }
              }
            }
            """);

        OpenClawModelConfigService.unregisterModelInRoot(root, "qwen/glm-4.7");

        assertThat(root.path("agents").path("defaults").path("models").has("qwen/glm-4.7")).isFalse();
        JsonNode fallbacks = root.path("agents").path("defaults").path("model").path("fallbacks");
        assertThat(fallbacks.isArray()).isTrue();
        assertThat(fallbacks).noneMatch(n -> "qwen/glm-4.7".equals(n.asText()));
        assertThat(fallbacks).anyMatch(n -> "qwen/qwen3.5-plus".equals(n.asText()));
        JsonNode qwenModels = root.path("models").path("providers").path("qwen").path("models");
        assertThat(qwenModels).noneMatch(n -> "glm-4.7".equals(n.path("id").asText()));
    }

    @Test
    void normalizeModelRef_mapsLegacyQwenPlus() {
        assertThat(OpenClawModelConfigService.normalizeModelRef("qwen/qwen-plus"))
            .isEqualTo("qwen/qwen3.5-plus");
    }

}
