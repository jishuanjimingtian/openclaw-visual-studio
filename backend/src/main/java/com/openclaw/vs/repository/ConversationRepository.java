package com.openclaw.vs.repository;

import com.openclaw.vs.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByArchivedFalseOrderByUpdatedAtDesc();

    @Query("SELECT c FROM Conversation c WHERE c.archived = false AND " +
           "(LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.tags) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Conversation> search(String keyword);

    @Query("SELECT c.model, COUNT(c) FROM Conversation c WHERE c.model IS NOT NULL AND c.model <> '' GROUP BY c.model")
    List<Object[]> countByModel();
}
