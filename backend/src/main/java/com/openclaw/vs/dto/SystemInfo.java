package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class SystemInfo {
    /** 操作系统信息 */
    private String os;
    /** CPU 信息 */
    private String cpu;
    /** 内存使用情况 */
    private String memory;
    /** 磁盘使用情况 */
    private String disk;
    /** Node.js 版本 */
    private String nodeVersion;
    /** Python 版本 */
    private String pythonVersion;
    /** Git 版本 */
    private String gitVersion;
}