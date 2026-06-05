package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatPartDto {
    /** text | image | file */
    private String type;
    private String text;
    private String attachmentId;
    private String name;
    private String mime;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private Boolean thumbReady;
}
