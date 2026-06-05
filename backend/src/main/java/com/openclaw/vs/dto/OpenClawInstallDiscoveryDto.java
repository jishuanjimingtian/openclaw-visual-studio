package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class OpenClawInstallDiscoveryDto {
    /** 是否在本机检测到 OpenClaw（CLI 或配置目录） */
    private boolean installed;

    /** 可用的 openclaw 命令（如 openclaw、npx openclaw 或绝对路径） */
    private String commandPath;

    /** where 解析出的实际可执行文件路径 */
    private String commandResolvedPath;

    /** openclaw --version 输出 */
    private String version;

    /** ~/.openclaw/openclaw.json 等配置路径 */
    private String configPath;

    /** 推测的工作目录 */
    private String workDir;

    /** Gateway 端口（来自配置或默认 18789） */
    private Integer gatewayPort;

    /** 安装方式：npm / github / external / unknown */
    private String installMethod;

    /** 人类可读的探测摘要 */
    private String message;
}
