package com.openclaw.vs.controller;

import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.model.Message;
import com.openclaw.vs.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conversations/{conversationId}/messages")
@RequiredArgsConstructor
@Tag(name = "消息管理", description = "会话消息 API")
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    @Operation(summary = "分页获取会话消息")
    public ApiResponse<Page<Message>> listMessages(
        @Parameter(description = "会话ID") @PathVariable String conversationId,
        @Parameter(description = "分页参数") @PageableDefault(size = 50, sort = "createdAt,asc") Pageable pageable) {
        return ApiResponse.success(messageService.listMessages(conversationId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单条消息")
    public ApiResponse<Message> getMessage(
        @Parameter(description = "消息ID") @PathVariable String id) {
        return ApiResponse.success(messageService.getMessage(id));
    }

    @PostMapping
    @Operation(summary = "创建消息")
    public ApiResponse<Message> createMessage(
        @Parameter(description = "会话ID") @PathVariable String conversationId,
        @Parameter(description = "消息内容") @Valid @RequestBody Message message) {
        message.setConversationId(conversationId);
        return ApiResponse.success(messageService.createMessage(message));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新消息")
    public ApiResponse<Message> updateMessage(
        @Parameter(description = "消息ID") @PathVariable String id,
        @Parameter(description = "更新的消息内容") @Valid @RequestBody Message message) {
        return ApiResponse.success(messageService.updateMessage(id, message));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除消息")
    public ApiResponse<Void> deleteMessage(
        @Parameter(description = "消息ID") @PathVariable String id) {
        messageService.deleteMessage(id);
        return ApiResponse.success(null);
    }
}