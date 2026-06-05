package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawWorkspaceConfigServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final OpenClawWorkspaceConfigService service = new OpenClawWorkspaceConfigService();

    @Test
    void ensureWorkspaceConfigured_writesMissingWorkspace(@TempDir Path temp) throws Exception {
        Path configDir = temp.resolve(".openclaw");
        Files.createDirectories(configDir);
        Path configPath = configDir.resolve("openclaw.json");
        Files.writeString(configPath, """
            {
              "gateway": { "port": 18789 }
            }
            """);

        String previousHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", temp.toString());

            OpenClawWorkspaceConfigService.WorkspaceResolution resolution =
                service.ensureWorkspaceConfigured(true);

            assertThat(resolution.autoConfigured()).isTrue();
            assertThat(resolution.configPath()).isEqualTo(configPath);
            assertThat(Files.isDirectory(resolution.skillsDir())).isTrue();

            var root = MAPPER.readTree(configPath.toFile());
            String configured = root.path("agents").path("defaults").path("workspace").asText();
            assertThat(configured.replace('\\', '/')).endsWith("/.openclaw/workspace");
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    void ensureWorkspaceConfigured_reusesExistingWorkspace(@TempDir Path temp) throws Exception {
        Path configDir = temp.resolve(".openclaw");
        Files.createDirectories(configDir);
        Path configPath = configDir.resolve("openclaw.json");
        Path workspace = temp.resolve("existing-workspace");
        Files.createDirectories(workspace.resolve("skills"));
        Files.writeString(configPath, """
            {
              "agents": {
                "defaults": {
                  "workspace": "%s"
                }
              }
            }
            """.formatted(workspace.toString().replace('\\', '/')));

        String previousHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", temp.toString());

            OpenClawWorkspaceConfigService.WorkspaceResolution resolution =
                service.ensureWorkspaceConfigured(true);

            assertThat(resolution.autoConfigured()).isFalse();
            assertThat(resolution.workspacePath()).isEqualTo(workspace);
            assertThat(resolution.skillsDir()).isEqualTo(workspace.resolve("skills"));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }
}
