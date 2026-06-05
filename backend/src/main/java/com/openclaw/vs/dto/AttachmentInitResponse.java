package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttachmentInitResponse {
    private String uploadId;
    private int chunkSize;
    private int maxChunks;
    /** Already uploaded chunk indices (for resume). */
    private int[] uploadedChunks;
}
