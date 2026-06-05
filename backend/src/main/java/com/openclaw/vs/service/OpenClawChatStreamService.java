package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.vs.dto.OpenClawChatEventDto;
import com.openclaw.vs.gateway.GatewayEventListener;
import com.openclaw.vs.gateway.GatewayWebSocketClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawChatStreamService {

  private final GatewayWebSocketClient gatewayClient;
  private final OpenClawChatService chatService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

  @PostConstruct
  public void registerGatewayListener() {
    GatewayEventListener listener =
        (event, payload, seq) -> {
          if (!"chat".equals(event)) {
            return;
          }
          OpenClawChatEventDto dto = chatService.mapChatEvent(payload);
          if (dto == null) {
            return;
          }
          broadcast(dto);
        };
    gatewayClient.addEventListener(listener);
  }

  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(0L);
    emitters.add(emitter);

    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(ex -> emitters.remove(emitter));

    try {
      emitter.send(SseEmitter.event().name("ready").data("{\"connected\":true}"));
    } catch (IOException e) {
      emitters.remove(emitter);
      emitter.completeWithError(e);
    }
    return emitter;
  }

  private void broadcast(OpenClawChatEventDto event) {
    if (emitters.isEmpty()) {
      return;
    }
    try {
      String json = objectMapper.writeValueAsString(event);
      for (SseEmitter emitter : emitters) {
        try {
          emitter.send(SseEmitter.event().name("chat").data(json));
        } catch (IOException e) {
          emitters.remove(emitter);
          emitter.completeWithError(e);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to broadcast chat event: {}", e.getMessage());
    }
  }
}
