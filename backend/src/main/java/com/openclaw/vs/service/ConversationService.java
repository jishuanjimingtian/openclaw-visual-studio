package com.openclaw.vs.service;

import com.openclaw.vs.dto.ConversationView;
import com.openclaw.vs.dto.OpenClawSessionDto;
import com.openclaw.vs.exception.NotFoundException;
import com.openclaw.vs.model.Conversation;
import com.openclaw.vs.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final OpenClawSessionService openClawSessionService;

    public Page<ConversationView> listConversations(Pageable pageable) {
        Page<Conversation> page = conversationRepository.findAll(pageable);
        List<ConversationView> views = page.getContent().stream().map(this::toView).toList();
        return new PageImpl<>(views, pageable, page.getTotalElements());
    }

    public ConversationView getConversation(String id) {
        return toView(getEntity(id));
    }

    public ConversationView createConversation(Conversation conversation) {
        conversation.setId(UUID.randomUUID().toString());
        return toView(conversationRepository.save(conversation));
    }

    public ConversationView updateConversation(String id, Conversation updated) {
        Conversation existing = getEntity(id);
        if (updated.getTitle() != null) existing.setTitle(updated.getTitle());
        if (updated.getModel() != null) existing.setModel(updated.getModel());
        if (updated.getTags() != null) existing.setTags(updated.getTags());
        existing.setArchived(updated.isArchived());
        return toView(conversationRepository.save(existing));
    }

    public void deleteConversation(String id) {
        if (!conversationRepository.existsById(id)) {
            throw new NotFoundException("会话", id);
        }
        conversationRepository.deleteById(id);
    }

    public List<ConversationView> syncFromOpenClaw() {
        var result = openClawSessionService.listSessions(100, null);
        for (OpenClawSessionDto session : result.getSessions()) {
            upsertFromOpenClaw(session);
        }
        return conversationRepository.findAll().stream().map(this::toView).toList();
    }

    private void upsertFromOpenClaw(OpenClawSessionDto session) {
        conversationRepository.findAll().stream()
            .filter(c -> session.getKey().equals(c.getOpenclawSessionKey()))
            .findFirst()
            .ifPresentOrElse(
                existing -> updateFromOpenClaw(existing, session),
                () -> conversationRepository.save(buildFromOpenClaw(session))
            );
    }

    private Conversation buildFromOpenClaw(OpenClawSessionDto session) {
        return Conversation.builder()
            .id(session.getSessionId() != null ? session.getSessionId() : UUID.randomUUID().toString())
            .title(session.getTitle())
            .model(session.getModel() != null ? session.getModel() : "default")
            .openclawSessionKey(session.getKey())
            .lastMessagePreview(session.getLastMessagePreview())
            .messageCount(0)
            .updatedAt(toLocalDateTime(session.getUpdatedAt()))
            .archived(false)
            .build();
    }

    private void updateFromOpenClaw(Conversation existing, OpenClawSessionDto session) {
        existing.setTitle(session.getTitle());
        if (session.getModel() != null) {
            existing.setModel(session.getModel());
        }
        existing.setOpenclawSessionKey(session.getKey());
        existing.setLastMessagePreview(session.getLastMessagePreview());
        if (session.getUpdatedAt() != null) {
            existing.setUpdatedAt(toLocalDateTime(session.getUpdatedAt()));
        }
        conversationRepository.save(existing);
    }

    private Conversation getEntity(String id) {
        return conversationRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("会话", id));
    }

    private ConversationView toView(Conversation c) {
        ConversationView view = new ConversationView();
        view.setId(c.getId());
        view.setTitle(c.getTitle());
        view.setModel(c.getModel());
        view.setCreatedAt(c.getCreatedAt());
        view.setUpdatedAt(c.getUpdatedAt());
        view.setTags(c.getTags());
        view.setArchived(c.isArchived());
        view.setOpenclawSessionKey(c.getOpenclawSessionKey());
        view.setLastMessagePreview(c.getLastMessagePreview());
        view.setMessageCount(c.getMessageCount());
        view.setFromOpenClaw(c.getOpenclawSessionKey() != null && !c.getOpenclawSessionKey().isBlank());
        return view;
    }

    private static LocalDateTime toLocalDateTime(Long epochMs) {
        if (epochMs == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
