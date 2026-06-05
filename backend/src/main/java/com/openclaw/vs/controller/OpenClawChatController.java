package com.openclaw.vs.controller;

import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.dto.ChatAbortRequest;
import com.openclaw.vs.dto.ChatResetRequest;
import com.openclaw.vs.dto.ChatSendRequest;
import com.openclaw.vs.dto.ChatSendResponse;
import com.openclaw.vs.dto.ChatSessionCreateResponse;
import com.openclaw.vs.dto.OpenClawChatStatusDto;
import com.openclaw.vs.service.OpenClawChatService;
import com.openclaw.vs.service.OpenClawChatStreamService;
import com.openclaw.vs.service.OpenClawSessionService;
import com.openclaw.vs.service.OpenClawUsageService;

import static com.openclaw.vs.service.OpenClawSessionService.DEFAULT_HISTORY_MAX_CHARS;
import com.openclaw.vs.dto.ChatMessageDto;
import com.openclaw.vs.dto.OpenClawSessionUsageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/openclaw/chat")
@RequiredArgsConstructor
@Tag(name = "OpenClaw 对话", description = "与 OpenClaw Gateway 实时对话")
public class OpenClawChatController {

  private final OpenClawChatService chatService;
  private final OpenClawChatStreamService streamService;
  private final OpenClawSessionService sessionService;
  private final OpenClawUsageService usageService;

  @GetMapping("/status")
  @Operation(summary = "Gateway 连接状态")
  public ApiResponse<OpenClawChatStatusDto> status() {
    return ApiResponse.success(chatService.getStatus());
  }

  @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "订阅 OpenClaw 聊天事件（SSE）")
  public SseEmitter events() {
    return streamService.subscribe();
  }

  @GetMapping("/history")
  @Operation(summary = "加载会话历史")
  public ApiResponse<List<ChatMessageDto>> history(
      @RequestParam String sessionKey,
      @RequestParam(defaultValue = "80") int limit,
      @RequestParam(defaultValue = "" + DEFAULT_HISTORY_MAX_CHARS) int maxChars,
      @RequestParam(defaultValue = "false") boolean light) {
    return ApiResponse.success(sessionService.getChatHistory(sessionKey, limit, maxChars, light));
  }

  @PostMapping("/send")
  @Operation(summary = "发送消息到 OpenClaw")
  public ApiResponse<ChatSendResponse> send(@Valid @RequestBody ChatSendRequest request)
      throws Exception {
    return ApiResponse.success(chatService.sendMessage(request));
  }

  @PostMapping("/abort")
  @Operation(summary = "中止当前回复")
  public ApiResponse<Map<String, Boolean>> abort(@Valid @RequestBody ChatAbortRequest request)
      throws Exception {
    chatService.abortChat(request);
    return ApiResponse.success(Map.of("aborted", true));
  }

  @GetMapping("/usage")
  @Operation(summary = "当前会话 Token 用量（来自 Gateway）")
  public ApiResponse<OpenClawSessionUsageDto> sessionUsage(@RequestParam String sessionKey) {
    return ApiResponse.success(usageService.getSessionUsage(sessionKey));
  }

  @PostMapping("/reset")
  @Operation(summary = "创建新会话（在会话列表中新增一条）")
  public ApiResponse<ChatSessionCreateResponse> reset(@Valid @RequestBody ChatResetRequest request)
      throws Exception {
    return ApiResponse.success(chatService.createNewSession(request.getSessionKey()));
  }
}
