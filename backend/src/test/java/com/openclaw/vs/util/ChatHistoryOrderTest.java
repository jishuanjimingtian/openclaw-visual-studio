package com.openclaw.vs.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.vs.dto.ChatMessageDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 chat.history 解析保持 Gateway 数组顺序（不按错误 timestamp 重排）。
 */
class ChatHistoryOrderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preservesGatewayArrayOrder_whenTimestampsAreOutOfOrder() throws Exception {
        var payload = mapper.readTree("""
            {
              "messages": [
                { "role": "user", "content": "first", "timestamp": 3000 },
                { "role": "assistant", "content": "second", "timestamp": 1000 },
                { "role": "user", "content": "third", "timestamp": 2000 }
              ]
            }
            """);

        List<ChatMessageDto> ordered = ChatHistoryOrder.parseVisibleInGatewayOrder(payload);
        assertThat(ordered).extracting(ChatMessageDto::getContent)
            .containsExactly("first", "second", "third");
    }
}
