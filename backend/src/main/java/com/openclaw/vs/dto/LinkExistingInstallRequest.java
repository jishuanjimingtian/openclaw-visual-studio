package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class LinkExistingInstallRequest {
    /** 可选：覆盖探测到的工作目录 */
    private String workDir;

    /** 可选：覆盖探测到的 Gateway 端口 */
    private Integer gatewayPort;
}
