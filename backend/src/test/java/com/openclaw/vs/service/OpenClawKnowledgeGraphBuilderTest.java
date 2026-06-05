package com.openclaw.vs.service;



import com.openclaw.vs.dto.KnowledgeGraphDto;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;



import java.nio.file.Files;

import java.nio.file.Path;



import static org.assertj.core.api.Assertions.assertThat;



class OpenClawKnowledgeGraphBuilderTest {



    private final OpenClawKnowledgePathGuard pathGuard = new OpenClawKnowledgePathGuard();

    private final OpenClawKnowledgeFileScanner fileScanner = new OpenClawKnowledgeFileScanner(pathGuard);

    private final OpenClawKnowledgeGraphBuilder builder =

        new OpenClawKnowledgeGraphBuilder(pathGuard, fileScanner);



    @Test

    void build_createsTemporalAndLinkEdges(@TempDir Path temp) throws Exception {

        Path workspace = temp.resolve("workspace");

        Files.createDirectories(workspace.resolve("memory"));

        Files.writeString(workspace.resolve("MEMORY.md"), """

            # Long term

            See [[memory/2026-06-01.md]]

            """);

        Files.writeString(workspace.resolve("memory/2026-06-01.md"), """

            # Day one

            #project wrote to MEMORY.md

            """);

        Files.writeString(workspace.resolve("memory/2026-06-02.md"), """

            # Day two

            #project continued

            """);



        KnowledgeGraphDto graph = builder.build(workspace, false);



        assertThat(graph.getNodes()).anyMatch(n -> "hub".equals(n.getKind()));

        assertThat(graph.getEdges()).anyMatch(e -> "temporal".equals(e.getKind()));

        assertThat(graph.getEdges()).anyMatch(e -> "link".equals(e.getKind()) || "promote".equals(e.getKind()));

        assertThat(graph.getMeta().getFileCount()).isEqualTo(3);

    }



    @Test

    void build_includesSoulAndDreamShard(@TempDir Path temp) throws Exception {

        Path workspace = temp.resolve("workspace");

        Files.createDirectories(workspace.resolve("memory/.dreams"));

        Files.writeString(workspace.resolve("SOUL.md"), "# Soul\nSee [[USER.md]]\n");

        Files.writeString(workspace.resolve("USER.md"), "# User\n");

        Files.writeString(workspace.resolve("memory/.dreams/fragment.md"), "# Fragment\n");



        KnowledgeGraphDto graph = builder.build(workspace, false);



        assertThat(graph.getNodes()).anyMatch(n -> "soul".equals(n.getKind()));

        assertThat(graph.getNodes()).anyMatch(n -> "dream_shard".equals(n.getKind()));

        assertThat(graph.getEdges()).anyMatch(e -> "link".equals(e.getKind()));

    }

}


