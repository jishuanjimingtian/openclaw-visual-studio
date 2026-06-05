package com.openclaw.vs.controller;

import com.openclaw.vs.model.ModelConfig;
import com.openclaw.vs.repository.ModelConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @BeforeEach
    void clean() {
        modelConfigRepository.deleteAll();
    }

    @Test
    void listModels_returnsOk() throws Exception {
        ModelConfig m = ModelConfig.builder()
            .id(UUID.randomUUID().toString())
            .name("Test Qwen")
            .provider("qwen")
            .endpoint("qwen/qwen3.5-plus")
            .enabled(true)
            .build();
        modelConfigRepository.save(m);

        mockMvc.perform(get("/models").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.content[0].modelRef").value("qwen/qwen3.5-plus"));
    }

    @Test
    void testModel_returnsResultWithMessage() throws Exception {
        ModelConfig m = ModelConfig.builder()
            .id(UUID.randomUUID().toString())
            .name("Test Qwen")
            .provider("qwen")
            .endpoint("qwen/qwen3.5-plus")
            .enabled(true)
            .build();
        modelConfigRepository.save(m);

        mockMvc.perform(post("/models/{id}/test", m.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.success").exists())
            .andExpect(jsonPath("$.data.message").isNotEmpty());
    }
}
