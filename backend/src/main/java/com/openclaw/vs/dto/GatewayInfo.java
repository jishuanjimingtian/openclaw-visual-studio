package com.openclaw.vs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayInfo {
    private String version;
    private Integer port;
    private String status;
    private Long pid;
    private String configPath;
    private String workDir;
    private String startTime;
    private String uptime;
    private String memoryUsage;
    private String endpoint;
    private String message;
    /** 后端 WebSocket RPC 客户端是否已与 Gateway 完成握手 */
    private Boolean wsConnected;
    /**
     * Gateway 由谁管理：vs-process（本工具子进程）、daemon（系统后台服务）、external（外部已运行实例）
     */
    private String managedBy;
    /** 是否已安装 OpenClaw Gateway 系统后台服务（Windows 计划任务 / systemd 等） */
    private Boolean serviceInstalled;
    /**
     * 异步启动阶段: idle | launching | port_wait | rpc_connect | running | failed
     */
    private String startupPhase;
    /** 启动进度 0-100 */
    private Integer startupProgress;
}