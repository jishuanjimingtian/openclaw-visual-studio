package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeGraphMetaDto {
    private String workspacePath;
    private String generatedAt;
    private int fileCount;
    private boolean includeChunks;
    /** Days of daily notes included in graph; 0 = all */
    private Integer dailyWindowDays;
    /** Daily notes omitted due to window filter */
    private Integer hiddenDailyCount;
}
