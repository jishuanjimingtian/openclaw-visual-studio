package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.ChatAbortRequest;
import com.openclaw.vs.dto.ChatSendRequest;
import com.openclaw.vs.dto.ChatSendResponse;
import com.openclaw.vs.dto.ChatSessionCreateResponse;
import com.openclaw.vs.dto.OpenClawChatEventDto;
import com.openclaw.vs.dto.OpenClawChatStatusDto;
import com.openclaw.vs.exception.BadRequestException;
import com.openclaw.vs.gateway.GatewayWebSocketClient;
import com.openclaw.vs.dto.ChatPartDto;
import com.openclaw.vs.util.ChatMessageMapper;
import com.openclaw.vs.util.GatewayChatPayloadBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawChatService {

  public static final String DEFAULT_SESSION_KEY = "agent:main:main";

  /** chat.send 在 Gateway 侧会立即返回 started；此处仅等待 RPC 确认 */
  private static final long CHAT_SEND_TIMEOUT_MS = 30_000;
  private static final long RPC_TIMEOUT_MS = 20_000;

  private final GatewayWebSocketClient gatewayClient;
  private final OpenClawSessionService sessionService;
  private final ChatAttachmentService attachmentService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public OpenClawChatStatusDto getStatus() {
    return OpenClawChatStatusDto.builder()
        .gatewayConnected(gatewayClient.isConnected())
        .defaultSessionKey(DEFAULT_SESSION_KEY)
        .build();
  }

  public ChatSendResponse sendMessage(ChatSendRequest request) throws Exception {
    ensureConnected();
    String runId =
        request.getRunId() != null && !request.getRunId().isBlank()
            ? request.getRunId().trim()
            : UUID.randomUUID().toString();

    ObjectNode params = buildSendParams(request, runId);
    gatewayClient.request("chat.send", params, CHAT_SEND_TIMEOUT_MS);
    sessionService.invalidateHistoryCache(request.getSessionKey().trim());

    return ChatSendResponse.builder()
        .runId(runId)
        .sessionKey(request.getSessionKey().trim())
        .build();
  }

  private ObjectNode buildSendParams(ChatSendRequest request, String runId) throws Exception {
    List<ChatPartDto> parts = request.getParts();
    boolean hasMediaParts = parts != null && parts.stream().anyMatch(this::isMediaPart);
    String message = request.getMessage() != null ? request.getMessage().trim() : "";

    if (hasMediaParts) {
      if (parts.size() > ChatAttachmentMimeGuard.maxAttachmentsPerMessage()) {
        throw new BadRequestException("单条消息最多 " + ChatAttachmentMimeGuard.maxAttachmentsPerMessage() + " 个附件");
      }
      for (ChatPartDto part : parts) {
        if (part == null || part.getType() == null) {
          continue;
        }
        String type = part.getType().trim().toLowerCase();
        if ("image".equals(type) || "file".equals(type)) {
          if (part.getAttachmentId() == null || part.getAttachmentId().isBlank()) {
            throw new BadRequestException("附件缺少 attachmentId");
          }
          attachmentService.resolveAttachmentPath(part.getAttachmentId().trim());
        }
      }
      if (message.isBlank()) {
        return GatewayChatPayloadBuilder.buildSendParams(
            objectMapper, request.getSessionKey().trim(), runId, parts, attachmentService);
      }
      List<ChatPartDto> merged = new java.util.ArrayList<>();
      merged.add(ChatPartDto.builder().type("text").text(message).build());
      merged.addAll(parts.stream().filter(p -> p != null && !"text".equalsIgnoreCase(p.getType())).toList());
      return GatewayChatPayloadBuilder.buildSendParams(
          objectMapper, request.getSessionKey().trim(), runId, merged, attachmentService);
    }

    if (message.isBlank()) {
      throw new BadRequestException("消息内容不能为空");
    }

    ObjectNode params = objectMapper.createObjectNode();
    params.put("sessionKey", request.getSessionKey().trim());
    params.put("message", message);
    params.put("deliver", false);
    params.put("idempotencyKey", runId);
    return params;
  }

  private boolean isMediaPart(ChatPartDto part) {
    if (part == null || part.getType() == null) {
      return false;
    }
    String type = part.getType().trim().toLowerCase();
    return "image".equals(type) || "file".equals(type);
  }

  public void abortChat(ChatAbortRequest request) throws Exception {
    ensureConnected();
    ObjectNode params = objectMapper.createObjectNode();
    params.put("sessionKey", request.getSessionKey().trim());
    if (request.getRunId() != null && !request.getRunId().isBlank()) {
      params.put("runId", request.getRunId().trim());
    }
    gatewayClient.request("chat.abort", params, RPC_TIMEOUT_MS);
  }

  /** 创建新的 OpenClaw 会话（fork 自当前会话），并在左侧会话列表中新增一条记录 */
  public ChatSessionCreateResponse createNewSession(String parentSessionKey) throws Exception {
    ensureConnected();
    if (parentSessionKey == null || parentSessionKey.isBlank()) {
      throw new BadRequestException("sessionKey 不能为空");
    }
    String parent = parentSessionKey.trim();
    abortSessionQuietly(parent);

    ObjectNode params = objectMapper.createObjectNode();
    params.put("agentId", parseAgentId(parent));
    params.put("parentSessionKey", parent);

    try {
      JsonNode payload = gatewayClient.request("sessions.create", params, RPC_TIMEOUT_MS);
      String newKey = payload.path("key").asText(null);
      if (newKey == null || newKey.isBlank()) {
        throw new BadRequestException("Gateway 未返回新会话 key");
      }
      sessionService.invalidateHistoryCache(newKey);
      return ChatSessionCreateResponse.builder().sessionKey(newKey).build();
    } catch (Exception e) {
      String msg = e.getMessage() != null ? e.getMessage() : "Gateway 创建会话失败";
      log.warn("sessions.create failed for parent={}: {}", parent, msg);
      throw new BadRequestException("新建对话失败: " + msg);
    }
  }

  /** @deprecated 保留供兼容；UI「新对话」应使用 {@link #createNewSession} */
  public void resetSession(String sessionKey) throws Exception {
    ensureConnected();
    if (sessionKey == null || sessionKey.isBlank()) {
      throw new BadRequestException("sessionKey 不能为空");
    }
    String key = sessionKey.trim();
    abortSessionQuietly(key);
    try {
      ObjectNode params = objectMapper.createObjectNode();
      params.put("key", key);
      params.put("reason", "new");
      gatewayClient.request("sessions.reset", params, RPC_TIMEOUT_MS);
      sessionService.invalidateHistoryCache(key);
    } catch (Exception e) {
      String msg = e.getMessage() != null ? e.getMessage() : "Gateway 重置会话失败";
      log.warn("sessions.reset failed for key={}: {}", key, msg);
      throw new BadRequestException("新建对话失败: " + msg);
    }
  }

  private void abortSessionQuietly(String sessionKey) {
    try {
      ObjectNode abortParams = objectMapper.createObjectNode();
      abortParams.put("sessionKey", sessionKey);
      gatewayClient.request("chat.abort", abortParams, RPC_TIMEOUT_MS);
    } catch (Exception e) {
      log.debug("chat.abort before reset ignored for {}: {}", sessionKey, e.getMessage());
    }
  }

  public OpenClawChatEventDto mapChatEvent(JsonNode payload) {
    if (payload == null || payload.isMissingNode()) {
      return null;
    }
    String state = payload.path("state").asText(null);
    if (state == null || state.isBlank()) {
      return null;
    }

    JsonNode messageNode = payload.path("message");
    String text = ChatMessageMapper.extractText(messageNode);
    if (text == null || text.isBlank()) {
      text = payload.path("text").asText(null);
    }
    text = ChatMessageMapper.humanizeStreamError(
        messageNode.isMissingNode() ? payload : messageNode,
        text != null ? text : "");
    String deltaText = payload.path("deltaText").asText(null);
    if ((text == null || text.isBlank()) && payload.has("messageText")) {
      text = payload.path("messageText").asText(null);
    }
    if ((text == null || text.isBlank()) && payload.has("errorMessage")) {
      text = payload.path("errorMessage").asText(null);
    }
    if (text != null && !text.isBlank()) {
      text = ChatMessageMapper.humanizeStreamError(
          messageNode.isMissingNode() ? payload : messageNode, text);
    }

    return OpenClawChatEventDto.builder()
        .runId(payload.path("runId").asText(null))
        .sessionKey(payload.path("sessionKey").asText(null))
        .state(state)
        .text(text)
        .deltaText(deltaText)
        .errorMessage(payload.path("errorMessage").asText(null))
        .build();
  }

  private static String parseAgentId(String sessionKey) {
    if (sessionKey == null || sessionKey.isBlank()) {
      return "main";
    }
    String[] parts = sessionKey.split(":");
    if (parts.length >= 2 && "agent".equals(parts[0]) && !parts[1].isBlank()) {
      return parts[1];
    }
    return "main";
  }

  private void ensureConnected() {
    if (!gatewayClient.isConnected()) {
      throw new IllegalStateException("Gateway WebSocket 未连接，请确认 OpenClaw Gateway 已启动");
    }
  }

}
