package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class EnvironmentCheckResult {
    /** 检测项名称 */
    private String checkName;
    /** 状态: pass / fail / warn */
    private String status;
    /** 详细信息 */
    private String message;
    /** 建议操作（可选） */
    private String suggestion;
}