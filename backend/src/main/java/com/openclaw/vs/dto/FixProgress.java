package com.openclaw.vs.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 修复进度
 */
@Data
public class FixProgress {
    /** 修复 ID */
    private String fixId;
    /** 当前阶段: queued / running / completed / failed */
    private String stage = "queued";
    /** 进度百分比 0-100 */
    private int percentage;
    /** 总修复项数 */
    private int totalItems;
    /** 已完成修复项数 */
    private int completedItems;
    /** 成功修复数 */
    private int successCount;
    /** 失败修复数 */
    private int failCount;
    /** 当前操作描述 */
    private String currentAction;
    /** 修复结果列表 */
    private List<EnvironmentFixResult> results = new ArrayList<>();
    /** 详细日志（全量或增量，取决于请求参数 logOffset） */
    private List<String> logs = new ArrayList<>();
    /** 服务端日志总行数（用于客户端增量合并） */
    private int logTotal;
}
