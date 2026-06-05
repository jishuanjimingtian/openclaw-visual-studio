package com.openclaw.vs.dto;

import com.openclaw.vs.model.Conversation;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationView extends Conversation {
    private boolean fromOpenClaw;
}
