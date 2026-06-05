package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachmentRefDto {
    private String attachmentId;
    private String name;
    private String mime;
    private long sizeBytes;
    private Integer width;
    private Integer height;
    private boolean thumbReady;
}
