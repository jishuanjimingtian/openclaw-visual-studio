package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemMetricsDto {
    private double cpu;
    private double memory;
    private double disk;
    /** JVM 运行秒数 */
    private long uptime;
    private int sessionCount;
    /** 今日 Token 总量（原始个数，前端自行格式化） */
    private long tokenUsage;
}
