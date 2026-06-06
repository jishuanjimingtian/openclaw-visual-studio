package com.openclaw.vs.gateway;

import com.openclaw.vs.dto.GatewayInfo;

/**
 * Gateway 健康评估：识别端口监听但 RPC 无响应的僵尸进程，并生成可操作的恢复提示。
 */
public final class GatewayHealthSupport {

    public static final long ZOMBIE_THRESHOLD_MS = 45_000;

    public static final String HEALTH_ZOMBIE = "zombie";
    public static final String HEALTH_RPC_DISCONNECTED = "rpc_disconnected";
    public static final String HEALTH_PROBE_FAILED = "probe_failed";

    public static final String ZOMBIE_RECOVERY_HINT =
        "Gateway 端口占用但长时间无响应，可能是计划任务僵尸进程。"
            + "请以管理员 PowerShell 依次执行："
            + " openclaw gateway stop；"
            + " schtasks /End /TN \"OpenClaw Gateway\"；"
            + " taskkill /PID <进程号> /F /T；"
            + " openclaw doctor --fix；"
            + " openclaw gateway start";

    private GatewayHealthSupport() {
    }

    public static long updatePortOpenTracking(boolean portReady, boolean clientReady, long currentSinceMs) {
        if (portReady && !clientReady) {
            return currentSinceMs == 0 ? System.currentTimeMillis() : currentSinceMs;
        }
        return 0;
    }

    public static GatewayInfo applyHealthAssessment(
        GatewayInfo info,
        long portOpenWithoutRpcSinceMs,
        boolean fullProbeAttemptedReconnect
    ) {
        if (info == null) {
            return null;
        }

        boolean wsConnected = Boolean.TRUE.equals(info.getWsConnected());
        if (wsConnected) {
            info.setHealthIssue(null);
            info.setRecoveryHint(null);
            return info;
        }

        boolean portReady = isPortLikelyReady(info);
        if (!portReady) {
            return info;
        }

        boolean likelyZombie = portOpenWithoutRpcSinceMs > 0
            && System.currentTimeMillis() - portOpenWithoutRpcSinceMs >= ZOMBIE_THRESHOLD_MS;

        if (likelyZombie) {
            info.setStatus("error");
            info.setHealthIssue(HEALTH_ZOMBIE);
            info.setRecoveryHint(ZOMBIE_RECOVERY_HINT);
            String pidHint = info.getPid() != null ? "（PID " + info.getPid() + "）" : "";
            info.setMessage("Gateway 无响应" + pidHint + "：端口已监听但 RPC 连接超时，进程可能已僵死。");
            return info;
        }

        if (fullProbeAttemptedReconnect || HEALTH_RPC_DISCONNECTED.equals(info.getHealthIssue())) {
            info.setHealthIssue(HEALTH_RPC_DISCONNECTED);
            if (info.getMessage() == null || info.getMessage().isBlank()) {
                info.setMessage("Gateway 端口已开放，RPC 未连接（检查 device.json / token / 端口）");
            }
        }

        return info;
    }

    private static boolean isPortLikelyReady(GatewayInfo info) {
        if (info.getEndpoint() != null && !info.getEndpoint().isBlank()) {
            return true;
        }
        String status = info.getStatus();
        return "starting".equals(status) || "running".equals(status) || "error".equals(status);
    }
}
