package com.openclaw.vs.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawConfigJsonWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void writePretty_replacesExistingFile() throws Exception {
        Path configPath = tempDir.resolve("openclaw.json");
        Files.writeString(configPath, "{\"old\":true}", StandardCharsets.UTF_8);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("agents", "ok");
        OpenClawConfigJsonWriter.writePretty(configPath, MAPPER, root);

        String written = Files.readString(configPath, StandardCharsets.UTF_8);
        assertThat(written).contains("\"agents\"");
        assertThat(written).doesNotContain("old");
    }
}
