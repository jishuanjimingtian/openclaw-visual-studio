package com.openclaw.vs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatResetRequest {
    @NotBlank(message = "sessionKey 不能为空")
    private String sessionKey;
}
