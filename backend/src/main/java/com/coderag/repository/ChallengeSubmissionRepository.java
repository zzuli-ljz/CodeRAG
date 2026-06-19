package com.coderag.repository;

import com.coderag.entity.ChallengeSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChallengeSubmissionRepository extends JpaRepository<ChallengeSubmission, Long> {

    List<ChallengeSubmission> findByUserIdAndChallengeIdOrderByCreatedAtDesc(Long userId, Long challengeId);

    List<ChallengeSubmission> findByUserIdOrderByCreatedAtDesc(Long userId);
}
