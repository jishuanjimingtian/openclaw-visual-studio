package com.openclaw.vs.repository;

import com.openclaw.vs.model.InstalledSkill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstalledSkillRepository extends JpaRepository<InstalledSkill, String> {

    Page<InstalledSkill> findBySourceOrderByDownloadsDesc(String source, Pageable pageable);

    List<InstalledSkill> findByStatus(String status);

    Optional<InstalledSkill> findByMarketSlug(String marketSlug);

    Page<InstalledSkill> findByStatus(String status, Pageable pageable);

    @Query("SELECT s FROM InstalledSkill s WHERE s.status = :status AND (" +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(s.marketSlug) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(CAST(s.description AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<InstalledSkill> searchInstalled(@Param("status") String status, @Param("keyword") String keyword, Pageable pageable);
}