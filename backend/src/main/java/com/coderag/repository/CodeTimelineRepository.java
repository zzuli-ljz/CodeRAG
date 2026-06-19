package com.coderag.repository;

import com.coderag.entity.CodeTimeline;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CodeTimelineRepository extends JpaRepository<CodeTimeline, Long> {

    Optional<CodeTimeline> findTopByUserIdAndRepoIdOrderByCreatedAtDesc(Long userId, Long repoId);

    Page<CodeTimeline> findByUserIdAndRepoIdOrderByCreatedAtDesc(Long userId, Long repoId, Pageable pageable);
}
