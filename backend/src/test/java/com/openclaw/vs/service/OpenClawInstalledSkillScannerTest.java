package com.openclaw.vs.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawInstalledSkillScannerTest {

    @Test
    void parseFrontmatter_readsBasicFields() {
        Map<String, String> meta = OpenClawInstalledSkillScanner.parseFrontmatter("""
            ---
            name: Self Improvement
            version: 2.1.0
            description: Helps agents improve
            ---
            # Body
            """);

        assertThat(meta.get("name")).isEqualTo("Self Improvement");
        assertThat(meta.get("version")).isEqualTo("2.1.0");
        assertThat(meta.get("description")).isEqualTo("Helps agents improve");
    }
}
