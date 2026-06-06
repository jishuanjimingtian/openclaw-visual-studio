package com.openclaw.vs.gateway;

import com.openclaw.vs.dto.GatewayInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayHealthSupportTest {

    @Test
    void updatePortOpenTracking_resetsWhenRpcConnected() {
        long since = GatewayHealthSupport.updatePortOpenTracking(true, false, 0);
        assertTrue(since > 0);
        assertEquals(0, GatewayHealthSupport.updatePortOpenTracking(true, true, since));
    }

    @Test
    void applyHealthAssessment_marksZombieAfterThreshold() {
        long since = System.currentTimeMillis() - GatewayHealthSupport.ZOMBIE_THRESHOLD_MS - 1_000;
        GatewayInfo info = GatewayInfo.builder()
            .port(18789)
            .status("starting")
            .wsConnected(false)
            .endpoint("http://127.0.0.1:18789")
            .pid(3664L)
            .build();

        GatewayHealthSupport.applyHealthAssessment(info, since, true);

        assertEquals("error", info.getStatus());
        assertEquals(GatewayHealthSupport.HEALTH_ZOMBIE, info.getHealthIssue());
        assertTrue(info.getMessage().contains("3664"));
        assertTrue(info.getRecoveryHint().contains("管理员"));
    }

    @Test
    void applyHealthAssessment_clearsIssueWhenConnected() {
        GatewayInfo info = GatewayInfo.builder()
            .status("running")
            .wsConnected(true)
            .healthIssue(GatewayHealthSupport.HEALTH_ZOMBIE)
            .recoveryHint("hint")
            .build();

        GatewayHealthSupport.applyHealthAssessment(info, 0, false);

        assertNull(info.getHealthIssue());
        assertNull(info.getRecoveryHint());
    }
}
