package com.openclaw.vs.service;

import com.openclaw.vs.config.SkillMarketProperties;
import com.openclaw.vs.dto.SkillMarketItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillLocalizationServiceTest {

    private SkillLocalizationService service;

    @BeforeEach
    void setUp() {
        SkillMarketProperties props = new SkillMarketProperties();
        props.setTranslateDescriptions(false);
        props.setTranslateNames(false);
        service = new SkillLocalizationService(props);
    }

    @Test
    void localizeItem_usesGlossaryForKnownSlug() {
        SkillMarketItemDto item = SkillMarketItemDto.builder()
            .slug("self-improving-agent")
            .name("Self-Improving Agent")
            .description("Captures learnings and errors for continuous agent improvement.")
            .build();

        service.localizeItem(item, false);

        assertThat(item.getName()).isEqualTo("自我改进助手");
        assertThat(item.isLocalized()).isTrue();
        assertThat(item.getDescription()).contains("智能体");
    }

    @Test
    void localizeItem_skipsWhenLocaleEnglish() {
        SkillMarketItemDto item = SkillMarketItemDto.builder()
            .slug("test-skill")
            .name("Browser Automation")
            .description("Headless browser tools for agents.")
            .build();
        String originalName = item.getName();

        service.localizeItems(java.util.List.of(item), "en", false);

        assertThat(item.getName()).isEqualTo(originalName);
    }

    @Test
    void isMostlyChinese_detectsChineseText() {
        assertThat(SkillLocalizationService.isMostlyChinese("这是一个中文技能说明文档")).isTrue();
        assertThat(SkillLocalizationService.isMostlyChinese("Browser automation for OpenClaw agents")).isFalse();
    }
}
