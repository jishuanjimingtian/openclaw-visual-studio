package com.openclaw.vs.gateway;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface GatewayEventListener {

    void onGatewayEvent(String event, JsonNode payload, Long seq);
}
