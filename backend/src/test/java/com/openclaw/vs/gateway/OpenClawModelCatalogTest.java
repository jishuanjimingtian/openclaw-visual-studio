package com.openclaw.vs.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawModelCatalogTest {

    @Test
    void listSuggestions_forQwen_returnsModelRefs() {
        var items = OpenClawModelCatalog.listSuggestions("qwen");
        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(d -> d.getModelRef().startsWith("qwen/"));
    }

    @Test
    void listSuggestions_forDeepSeek_returnsModelRefs() {
        var items = OpenClawModelCatalog.listSuggestions("deepseek");
        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(d -> d.getModelRef().startsWith("deepseek/"));
        assertThat(items).anyMatch(d -> "deepseek/deepseek-v4-flash".equals(d.getModelRef()));
    }

    @Test
    void listSuggestions_forOpenAi_returnsModels() {
        var items = OpenClawModelCatalog.listSuggestions("openai");
        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(d -> d.getModelRef().startsWith("openai/"));
    }

    @Test
    void listMarket_forDomesticCategory_filtersCorrectly() {
        var items = OpenClawModelCatalog.listMarket("domestic", null, null);
        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(d -> "domestic".equals(d.getCategory()));
    }

    @Test
    void listSuggestions_forUnknownProvider_isEmpty() {
        assertThat(OpenClawModelCatalog.listSuggestions("unknown-xyz")).isEmpty();
    }
}
