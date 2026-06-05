package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenClawChatEventDto {

  private String runId;
  private String sessionKey;
  /** delta | final | aborted | error */
  private String state;
  /** 累积助手文本（delta/final） */
  private String text;
  /** 增量文本（若 Gateway 提供） */
  private String deltaText;
  private String errorMessage;
}
