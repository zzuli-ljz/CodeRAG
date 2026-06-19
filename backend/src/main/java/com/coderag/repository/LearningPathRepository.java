package com.coderag.repository;

import com.coderag.entity.LearningPath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    Optional<LearningPath> findTopByUserIdAndRepoIdOrderByCreatedAtDesc(Long userId, Long repoId);

    List<LearningPath> findByUserIdAndRepoIdOrderByCreatedAtDesc(Long userId, Long repoId);
}
