package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatSessionCreateResponse {
    private String sessionKey;
}
