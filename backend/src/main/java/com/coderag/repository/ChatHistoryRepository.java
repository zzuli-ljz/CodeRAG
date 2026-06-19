package com.coderag.repository;

import com.coderag.entity.ChatHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    Page<ChatHistory> findByUserIdAndRepoIdOrderByCreatedAtDesc(Long userId, Long repoId, Pageable pageable);

    long countByUserIdAndCreatedAtBetween(Long userId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    long countByUserId(Long userId);
}
