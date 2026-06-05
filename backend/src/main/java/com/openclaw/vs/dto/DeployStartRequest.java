package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class DeployStartRequest {
    /** 安装来源: npm 或 github */
    private String installSource = "npm";
    /** Gateway 端口 */
    private int gatewayPort = 18789;
    /** 工作目录 */
    private String workDir;
}