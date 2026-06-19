package com.coderag.repository;

import com.coderag.entity.CodeChallenge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeChallengeRepository extends JpaRepository<CodeChallenge, Long> {

    Page<CodeChallenge> findByUserIdAndRepoIdOrderByCreatedAtDesc(Long userId, Long repoId, Pageable pageable);

    List<CodeChallenge> findByUserIdAndRepoIdOrderByCreatedAtDesc(Long userId, Long repoId);

    Page<CodeChallenge> findByRepoIdOrderByCreatedAtDesc(Long repoId, Pageable pageable);
}
