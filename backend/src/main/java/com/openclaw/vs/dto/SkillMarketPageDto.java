package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SkillMarketPageDto {
    private List<SkillMarketItemDto> items;
    private String nextCursor;
    private int totalCount;
    private boolean fromCache;
    private String source;
    private String sort;
    private String locale;
    private boolean localized;
}
