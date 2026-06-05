package com.openclaw.vs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AttachmentInitRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String mime;
    @Positive
    private long sizeBytes;
    private String sha256;
}
