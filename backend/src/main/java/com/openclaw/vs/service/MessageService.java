package com.openclaw.vs.service;

import com.openclaw.vs.exception.NotFoundException;
import com.openclaw.vs.model.Message;
import com.openclaw.vs.repository.ConversationRepository;
import com.openclaw.vs.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    public Page<Message> listMessages(String conversationId, Pageable pageable) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new NotFoundException("会话", conversationId);
        }
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable);
    }

    public Message getMessage(String id) {
        return messageRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("消息", id));
    }

    public Message createMessage(Message message) {
        if (!conversationRepository.existsById(message.getConversationId())) {
            throw new NotFoundException("会话", message.getConversationId());
        }
        message.setId(UUID.randomUUID().toString());
        return messageRepository.save(message);
    }

    public Message updateMessage(String id, Message updated) {
        Message existing = getMessage(id);
        if (updated.getContent() != null) existing.setContent(updated.getContent());
        if (updated.getTokens() != null) existing.setTokens(updated.getTokens());
        return messageRepository.save(existing);
    }

    public void deleteMessage(String id) {
        if (!messageRepository.existsById(id)) {
            throw new NotFoundException("消息", id);
        }
        messageRepository.deleteById(id);
    }
}