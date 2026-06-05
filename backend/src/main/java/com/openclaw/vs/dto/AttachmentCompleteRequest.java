package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class AttachmentCompleteRequest {
    private String sha256;
    private Integer width;
    private Integer height;
}
