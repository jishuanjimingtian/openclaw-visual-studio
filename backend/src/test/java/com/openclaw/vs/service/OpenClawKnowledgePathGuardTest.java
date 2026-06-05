package com.openclaw.vs.service;



import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;



import java.nio.file.Path;



import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;



class OpenClawKnowledgePathGuardTest {



    private final OpenClawKnowledgePathGuard guard = new OpenClawKnowledgePathGuard();



    @Test

    void allowsMemoryFiles() {

        assertThat(guard.isAllowedRelative("MEMORY.md")).isTrue();

        assertThat(guard.isAllowedRelative("DREAMS.md")).isTrue();

        assertThat(guard.isAllowedRelative("SOUL.md")).isTrue();

        assertThat(guard.isAllowedRelative("USER.md")).isTrue();

        assertThat(guard.isAllowedRelative("AGENTS.md")).isTrue();

        assertThat(guard.isAllowedRelative("memory/2026-06-02.md")).isTrue();

        assertThat(guard.isAllowedRelative("memory/2026-06-02-notes.md")).isTrue();

        assertThat(guard.isAllowedRelative("memory/.dreams/2026-06-02.md")).isTrue();

    }



    @Test

    void rejectsUnsafePaths() {

        assertThat(guard.isAllowedRelative("../etc/passwd")).isFalse();

        assertThat(guard.isAllowedRelative("memory/.dreams/sub/x.md")).isFalse();

        assertThat(guard.isAllowedRelative("skills/foo.md")).isFalse();

        assertThat(guard.isAllowedRelative("notes.txt")).isFalse();

    }



    @Test

    void resolveInWorkspace_blocksTraversal(@TempDir Path temp) {

        Path workspace = temp.resolve("ws");

        assertThatThrownBy(() -> guard.resolveInWorkspace(workspace, "../outside.md"))

            .isInstanceOf(IllegalArgumentException.class);

    }



    @Test

    void fileKind_mapsCorrectly() {

        assertThat(guard.fileKind("MEMORY.md")).isEqualTo("hub");

        assertThat(guard.fileKind("memory/2026-01-01.md")).isEqualTo("daily");

        assertThat(guard.fileKind("DREAMS.md")).isEqualTo("dream");

        assertThat(guard.fileKind("SOUL.md")).isEqualTo("soul");

        assertThat(guard.fileKind("USER.md")).isEqualTo("user");

        assertThat(guard.fileKind("AGENTS.md")).isEqualTo("agents");

        assertThat(guard.fileKind("memory/.dreams/night.md")).isEqualTo("dream_shard");

    }

}


