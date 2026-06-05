package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class StartDeploymentRequest {
    /** 安装来源: npm 或 github */
    private String installSource = "npm";
    /** Gateway 端口 */
    private int gatewayPort = 18789;
    /** 工作目录 */
    private String workDir;
    /** 是否自动修复缺失环境 */
    private boolean autoFix = false;
}