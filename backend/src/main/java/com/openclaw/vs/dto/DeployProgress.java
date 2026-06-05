package com.openclaw.vs.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class DeployProgress {
    /** 部署阶段: checking / installing / configuring / completed / failed / cancelled */
    private String stage;
    /** 进度百分比 (0-100) */
    private int percentage;
    /** 当前执行的操作描述 */
    private String currentAction;
    /** 日志列表（全量或增量，取决于请求参数 logOffset） */
    private List<String> logs = new ArrayList<>();
    /** 服务端日志总行数（用于客户端增量合并） */
    private int logTotal;
    /** 部署摘要（完成后填充） */
    private DeploySummary summary;
}