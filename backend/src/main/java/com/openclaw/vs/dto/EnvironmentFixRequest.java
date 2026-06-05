package com.openclaw.vs.dto;

import lombok.Data;
import java.util.List;

/**
 * 环境修复请求
 */
@Data
public class EnvironmentFixRequest {
    /** 要修复的检测项名称列表（为空则修复所有可修复项） */
    private List<String> fixItems;
    /** 是否自动修复（跳过确认） */
    private boolean autoFix = true;
}
