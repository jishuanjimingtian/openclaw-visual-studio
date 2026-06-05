package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeGraphNodeDto {
    private String id;
    private String label;
    private String kind;
    private String path;
    private Integer lineStart;
    private Integer size;
    private List<String> tags;
    private String updatedAt;
}
