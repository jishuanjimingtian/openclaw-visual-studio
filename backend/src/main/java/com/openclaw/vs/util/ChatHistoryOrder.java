package com.openclaw.vs.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.openclaw.vs.dto.ChatMessageDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 按 Gateway chat.history 数组原始顺序解析可见消息，不按 timestamp 重排。
 */
public final class ChatHistoryOrder {

    private ChatHistoryOrder() {}

    public static List<ChatMessageDto> parseVisibleInGatewayOrder(JsonNode payload) {
        List<ChatMessageDto> out = new ArrayList<>();
        JsonNode messagesNode = ChatMessageMapper.resolveMessagesArray(payload);
        if (messagesNode.isArray()) {
            for (JsonNode msg : messagesNode) {
                ChatMessageDto dto = ChatMessageMapper.mapMessage(msg);
                if (ChatMessageMapper.isVisible(dto)) {
                    out.add(dto);
                }
            }
        }
        return out;
    }
}
