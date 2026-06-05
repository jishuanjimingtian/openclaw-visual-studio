package com.openclaw.vs.service;

import com.openclaw.vs.dto.AgentRoleDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawAgentConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void listRoles_discoversMainAgentFromDiskWhenListMissing() throws Exception {
        Path configDir = tempDir.resolve(".openclaw");
        Files.createDirectories(configDir);
        Path configPath = configDir.resolve("openclaw.json");
        Path agentHome = configDir.resolve("agents").resolve("main");
        Files.createDirectories(agentHome.resolve("sessions"));
        Files.writeString(configPath, """
            {
              "agents": {
                "defaults": {
                  "workspace": "%s",
                  "model": { "primary": "deepseek/deepseek-v4-pro" },
                  "params": { "temperature": 0.7, "maxTokens": 2048 }
                }
              }
            }
            """.formatted(agentHome.getParent().getParent().resolve("workspace").toString().replace("\\", "\\\\")));

        OpenClawAgentConfigService service = createService(configPath);
        var roles = service.listRoles();

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).getId()).isEqualTo(OpenClawAgentConfigService.DEFAULT_AGENT_ID);
        assertThat(roles.get(0).isDefaultAgent()).isTrue();
        assertThat(roles.get(0).getDefaultModel()).isEqualTo("deepseek/deepseek-v4-pro");
    }

    @Test
    void listRoles_readsDefaultsWhenAgentsListMissing() throws Exception {
        Path configPath = tempDir.resolve("openclaw.json");
        Files.writeString(configPath, """
            {
              "agents": {
                "defaults": {
                  "workspace": "/tmp/ws",
                  "timeoutSeconds": 120,
                  "model": { "primary": "deepseek/deepseek-v4-flash" },
                  "params": { "temperature": 0.4, "maxTokens": 4096 },
                  "systemPrompt": "你是助手"
                }
              }
            }
            """);

        OpenClawAgentConfigService service = createService(configPath);
        var roles = service.listRoles();

        assertThat(roles).hasSize(1);
        AgentRoleDto role = roles.get(0);
        assertThat(role.getId()).isEqualTo(OpenClawAgentConfigService.DEFAULTS_ROLE_ID);
        assertThat(role.getName()).isEqualTo("默认 Agent");
        assertThat(role.getSystemPrompt()).isEqualTo("你是助手");
        assertThat(role.getPromptSource()).isEqualTo("config");
        assertThat(role.getTemperature()).isEqualTo(0.4);
        assertThat(role.getMaxTokens()).isEqualTo(4096);
        assertThat(role.getDefaultModel()).isEqualTo("deepseek/deepseek-v4-flash");
        assertThat(role.isOpenclaw()).isTrue();
    }

    @Test
    void saveRole_writesDefaultsToConfig() throws Exception {
        Path configPath = tempDir.resolve("openclaw.json");
        Files.writeString(configPath, """
            { "agents": { "defaults": { "model": { "primary": "qwen/qwen3.5-plus" } } } }
            """);

        OpenClawAgentConfigService service = createService(configPath);
        service.saveRole(AgentRoleDto.builder()
            .id(OpenClawAgentConfigService.DEFAULTS_ROLE_ID)
            .name("默认 Agent")
            .systemPrompt("新的系统提示")
            .temperature(0.2)
            .maxTokens(8192)
            .defaultModel("deepseek/deepseek-v4-flash")
            .build());

        AgentRoleDto saved = service.getRole(OpenClawAgentConfigService.DEFAULTS_ROLE_ID);
        assertThat(saved.getSystemPrompt()).isEqualTo("新的系统提示");
        assertThat(saved.getTemperature()).isEqualTo(0.2);
        assertThat(saved.getMaxTokens()).isEqualTo(8192);
        assertThat(saved.getDefaultModel()).isEqualTo("deepseek/deepseek-v4-flash");
    }

    @Test
    void listRoles_readsAgentsListEntries() throws Exception {
        Path configPath = tempDir.resolve("openclaw.json");
        Files.writeString(configPath, """
            {
              "agents": {
                "defaults": { "model": { "primary": "qwen/qwen3.5-plus" } },
                "list": [
                  {
                    "id": "work",
                    "name": "工作助手",
                    "default": true,
                    "model": "deepseek/deepseek-v4-flash",
                    "systemPrompt": "专注工作",
                    "params": { "temperature": 0.1, "maxTokens": 2048 }
                  }
                ]
              }
            }
            """);

        OpenClawAgentConfigService service = createService(configPath);
        var roles = service.listRoles();

        assertThat(roles).hasSize(2);
        assertThat(roles.stream().anyMatch(r -> "work".equals(r.getId()))).isTrue();
        assertThat(roles.stream().anyMatch(r -> OpenClawAgentConfigService.DEFAULTS_ROLE_ID.equals(r.getId()))).isTrue();
    }

    @Test
    void syncBootstrapPrompt_writesAgentsMdToDefaults() throws Exception {
        Path configPath = tempDir.resolve("openclaw.json");
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("AGENTS.md"), "# Hello\n\nBe helpful.\n");
        Files.writeString(configPath, """
            {
              "agents": {
                "defaults": {
                  "workspace": "%s"
                }
              }
            }
            """.formatted(workspace.toString().replace("\\", "\\\\")));

        OpenClawAgentConfigService service = createService(configPath);
        var result = service.syncBootstrapPromptToConfig(null);

        assertThat(result.getCharCount()).isGreaterThan(0);
        assertThat(result.getTargetField()).contains("agents.defaults.systemPrompt");
        AgentRoleDto role = service.getRole(OpenClawAgentConfigService.DEFAULTS_ROLE_ID);
        assertThat(role.getPromptSource()).isEqualTo("config");
        assertThat(role.getSystemPrompt()).contains("Be helpful");
    }

    @Test
    void initDefaultAgentsMd_createsFileWhenMissing() throws Exception {
        Path configPath = tempDir.resolve("openclaw.json");
        Path workspace = tempDir.resolve("ws");
        Files.writeString(configPath, """
            { "agents": { "defaults": { "workspace": "%s" } } }
            """.formatted(workspace.toString().replace("\\", "\\\\")));

        OpenClawAgentConfigService service = createService(configPath);
        var result = service.initDefaultAgentsMd();

        assertThat(Files.isRegularFile(workspace.resolve("AGENTS.md"))).isTrue();
        assertThat(result.getMessage()).contains("已创建");
    }

    private OpenClawAgentConfigService createService(Path configPath) {
        OpenClawModelConfigService modelService = new OpenClawModelConfigService() {
            @Override
            public Path getConfigPath() {
                return configPath;
            }

            @Override
            public void applyModelToConfig(
                String modelRef,
                boolean register,
                boolean setAsPrimary,
                String plainApiKey,
                java.util.List<String> fallbackModelRefs,
                String baseUrl
            ) {
                // no-op for unit tests
            }
        };
        return new OpenClawAgentConfigService(modelService);
    }
}
