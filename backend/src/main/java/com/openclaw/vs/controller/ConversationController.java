package com.openclaw.vs.controller;

import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.dto.ChatMessageDto;
import com.openclaw.vs.dto.ConversationView;
import com.openclaw.vs.dto.PageResult;
import com.openclaw.vs.dto.OpenClawSessionsResult;
import com.openclaw.vs.model.Conversation;
import com.openclaw.vs.service.ConversationService;
import com.openclaw.vs.service.OpenClawSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
@Tag(name = "会话管理", description = "OpenClaw 会话管理 API")
public class ConversationController {

    private final ConversationService conversationService;
    private final OpenClawSessionService openClawSessionService;

    @GetMapping
    @Operation(summary = "分页查询会话列表")
    public ApiResponse<PageResult<ConversationView>> listConversations(
        @Parameter(description = "分页参数") @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(PageResult.from(conversationService.listConversations(pageable)));
    }

    @GetMapping("/openclaw/sessions")
    @Operation(summary = "从 Gateway 读取 OpenClaw 会话列表（与会话框一致）")
    public ApiResponse<OpenClawSessionsResult> listOpenClawSessions(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "true") boolean includePreview) {
        return ApiResponse.success(openClawSessionService.listSessions(limit, search, includePreview));
    }

    @GetMapping("/openclaw/history")
    @Operation(summary = "读取 OpenClaw 会话聊天记录")
    public ApiResponse<java.util.List<ChatMessageDto>> getOpenClawHistory(
        @RequestParam String sessionKey,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "12000") int maxChars,
        @RequestParam(defaultValue = "false") boolean light) throws Exception {
        return ApiResponse.success(openClawSessionService.getChatHistory(sessionKey, limit, maxChars, light));
    }

    @PostMapping("/openclaw/sync")
    @Operation(summary = "将 OpenClaw 会话同步到本地数据库")
    public ApiResponse<java.util.List<ConversationView>> syncFromOpenClaw() {
        return ApiResponse.success(conversationService.syncFromOpenClaw());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取会话详情")
    public ApiResponse<ConversationView> getConversation(
        @Parameter(description = "会话ID") @PathVariable String id) {
        return ApiResponse.success(conversationService.getConversation(id));
    }

    @PostMapping
    @Operation(summary = "创建新会话")
    public ApiResponse<ConversationView> createConversation(
        @Parameter(description = "会话信息") @Valid @RequestBody Conversation conversation) {
        return ApiResponse.success(conversationService.createConversation(conversation));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新会话")
    public ApiResponse<ConversationView> updateConversation(
        @Parameter(description = "会话ID") @PathVariable String id,
        @Parameter(description = "更新的会话信息") @Valid @RequestBody Conversation conversation) {
        return ApiResponse.success(conversationService.updateConversation(id, conversation));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除会话")
    public ApiResponse<Void> deleteConversation(
        @Parameter(description = "会话ID") @PathVariable String id) {
        conversationService.deleteConversation(id);
        return ApiResponse.success(null);
    }
}