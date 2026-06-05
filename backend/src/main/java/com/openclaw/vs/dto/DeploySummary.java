package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class DeploySummary {
    /** OpenClaw 版本 */
    private String version;
    /** 安装路径 */
    private String path;
    /** Gateway 端口 */
    private int gatewayPort;
    /** 配置文件路径 */
    private String configPath;
    /** 工作目录 */
    private String workDir;
    /** 安装来源: npm / github */
    private String installSource;
}