package com.coderag.repository;

import com.coderag.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByRepoId(Long repoId);

    List<QuizQuestion> findByRepoIdOrderByCreatedAtDesc(Long repoId);

    List<QuizQuestion> findByRepoIdAndDifficulty(Long repoId, String difficulty);
}
