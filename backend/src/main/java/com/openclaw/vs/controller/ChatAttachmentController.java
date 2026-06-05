package com.openclaw.vs.controller;

import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.dto.AttachmentCompleteRequest;
import com.openclaw.vs.dto.AttachmentInitRequest;
import com.openclaw.vs.dto.AttachmentInitResponse;
import com.openclaw.vs.dto.ChatAttachmentRefDto;
import com.openclaw.vs.service.ChatAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@RestController
@RequestMapping("/openclaw/chat/attachments")
@RequiredArgsConstructor
@Tag(name = "OpenClaw 对话附件", description = "分片上传与附件下载")
public class ChatAttachmentController {

    private final ChatAttachmentService attachmentService;

    @PostMapping("/init")
    @Operation(summary = "初始化分片上传")
    public ApiResponse<AttachmentInitResponse> init(@Valid @RequestBody AttachmentInitRequest request)
        throws Exception {
        return ApiResponse.success(attachmentService.initUpload(request));
    }

    @PutMapping("/{uploadId}/chunks/{index}")
    @Operation(summary = "上传单个分片（application/octet-stream）")
    public ApiResponse<Void> uploadChunk(
        @PathVariable String uploadId,
        @PathVariable int index,
        HttpServletRequest request
    ) throws Exception {
        long len = request.getContentLengthLong();
        attachmentService.writeChunk(uploadId, index, request.getInputStream(), len);
        return ApiResponse.success(null);
    }

    @PostMapping("/{uploadId}/complete")
    @Operation(summary = "完成上传并落盘")
    public ApiResponse<ChatAttachmentRefDto> complete(
        @PathVariable String uploadId,
        @RequestBody(required = false) AttachmentCompleteRequest body
    ) throws Exception {
        return ApiResponse.success(attachmentService.completeUpload(uploadId, body));
    }

    @GetMapping("/{id}")
    @Operation(summary = "下载原文件（支持 Range）")
    public ResponseEntity<Resource> download(@PathVariable String id) throws Exception {
        Path file = attachmentService.resolveAttachmentPath(id);
        ChatAttachmentRefDto ref = attachmentService.getRef(id);
        FileSystemResource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
            .contentType(MediaType.parseMediaType(ref.getMime()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + ref.getName() + "\"")
            .body(resource);
    }

    @GetMapping("/{id}/thumb")
    @Operation(summary = "获取缩略图")
    public ResponseEntity<Resource> thumb(@PathVariable String id) throws Exception {
        Path thumb = attachmentService.resolveThumbPath(id);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
            .body(new FileSystemResource(thumb));
    }
}
