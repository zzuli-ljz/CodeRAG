package com.coderag.repository;

import com.coderag.entity.TranslationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranslationHistoryRepository extends JpaRepository<TranslationHistory, Long> {

    Page<TranslationHistory> findByUserIdAndRepoIdOrderByCreatedAtDesc(Long userId, Long repoId, Pageable pageable);
}
