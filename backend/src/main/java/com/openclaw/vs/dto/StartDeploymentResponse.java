package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class StartDeploymentResponse {
    /** 部署任务 ID */
    private String deployId;
    /** 响应消息 */
    private String message;
}