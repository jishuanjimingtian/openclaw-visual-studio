package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单项修复结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentFixResult {
    /** 检测项名称 */
    private String itemName;
    /** 修复状态: success / fail / skipped */
    private String status;
    /** 修复详情（日志消息） */
    private String detail;
    /** 修复后状态: pass / fail / warn */
    private String afterFixStatus;
    /** 修复后消息 */
    private String afterFixMessage;
}
