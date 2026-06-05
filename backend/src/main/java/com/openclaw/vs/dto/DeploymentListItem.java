package com.openclaw.vs.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeploymentListItem {
    /** 部署ID */
    private String id;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    /** OpenClaw 版本 */
    private String version;
    
    /** 安装方式: npm / github */
    private String installMethod;
    
    /** Gateway 端口 */
    private Integer port;
    
    /** 工作目录 */
    private String workDir;
    
    /** 部署状态: pending / running / completed / failed / cancelled */
    private String status;
    
    /** Gateway 是否正在运行 */
    private Boolean gatewayRunning;
    
    /** 部署摘要（可选） */
    private DeploySummary summary;
}