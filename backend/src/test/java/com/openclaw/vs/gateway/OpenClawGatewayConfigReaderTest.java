package com.openclaw.vs.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawGatewayConfigReaderTest {

    @Test
    void deriveBrowserControlPort_offsetsFromGatewayPort() {
        assertThat(OpenClawGatewayConfigReader.deriveBrowserControlPort(18789))
            .isEqualTo(18791);
        assertThat(OpenClawGatewayConfigReader.deriveBrowserControlPort(18791))
            .isEqualTo(18793);
    }

    @Test
    void toWebSocketUrl_usesPortWithoutPathSuffix() {
        assertThat(OpenClawGatewayConfigReader.toWebSocketUrl(18789))
            .isEqualTo("ws://127.0.0.1:18789");
        assertThat(OpenClawGatewayConfigReader.toWebSocketUrl(18791))
            .isEqualTo("ws://127.0.0.1:18791");
    }

    @Test
    void normalizeWebSocketUrl_stripsLegacyWsPath() {
        assertThat(OpenClawGatewayConfigReader.normalizeWebSocketUrl("ws://localhost:18791/ws"))
            .isEqualTo("ws://localhost:18791");
        assertThat(OpenClawGatewayConfigReader.normalizeWebSocketUrl("ws://127.0.0.1:18789"))
            .isEqualTo("ws://127.0.0.1:18789");
    }
}
