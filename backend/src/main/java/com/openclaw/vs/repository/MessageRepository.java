package com.openclaw.vs.repository;

import com.openclaw.vs.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    Page<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId, Pageable pageable);

    void deleteByConversationId(String conversationId);

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT COALESCE(SUM(m.tokens), 0) FROM Message m WHERE m.tokens IS NOT NULL AND m.createdAt >= :since")
    long sumTokensSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(m.tokens), 0) FROM Message m WHERE m.tokens IS NOT NULL AND m.createdAt >= :start AND m.createdAt < :end")
    long sumTokensBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
        SELECT c.model, COUNT(DISTINCT c.id), COALESCE(SUM(m.tokens), 0), COUNT(m.id)
        FROM Conversation c
        LEFT JOIN Message m ON m.conversationId = c.id
        WHERE c.model IS NOT NULL AND c.model <> ''
        GROUP BY c.model
        """)
    List<Object[]> aggregateUsageByModel();

    @Query("""
        SELECT c.id, c.title, c.model, COALESCE(SUM(m.tokens), 0), COUNT(m.id), c.updatedAt
        FROM Conversation c
        LEFT JOIN Message m ON m.conversationId = c.id
        WHERE c.archived = false
        GROUP BY c.id, c.title, c.model, c.updatedAt
        ORDER BY COALESCE(SUM(m.tokens), 0) DESC
        """)
    List<Object[]> topConversationsByTokens(Pageable pageable);
}