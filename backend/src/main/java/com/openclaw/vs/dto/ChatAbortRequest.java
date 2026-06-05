package com.openclaw.vs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatAbortRequest {

  @NotBlank private String sessionKey;

  private String runId;
}
