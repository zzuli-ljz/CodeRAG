package com.coderag.repository;

import com.coderag.entity.QuizAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    Page<QuizAttempt> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 错题本：按用户+错误状态查询 */
    Page<QuizAttempt> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status, Pageable pageable);

    /** 收藏列表 */
    @Query("SELECT qa FROM QuizAttempt qa WHERE qa.userId = :userId AND qa.status = 'FAVORITE' ORDER BY qa.createdAt DESC")
    Page<QuizAttempt> findFavorites(@Param("userId") Long userId, Pageable pageable);

    /** 错题本：多条件筛选（通过 quizId 关联 quiz_questions 表） */
    @Query(value = """
            SELECT qa.* FROM quiz_attempts qa
            INNER JOIN quiz_questions qq ON qa.quiz_id = qq.id
            WHERE qa.user_id = :userId AND qa.status = 'WRONG_BOOK'
            AND (:difficulty IS NULL OR qq.difficulty = :difficulty)
            AND (:knowledgePoint IS NULL OR qq.knowledge_point = :knowledgePoint)
            AND (:repoId IS NULL OR qq.repo_id = :repoId)
            AND (:keyword IS NULL OR qq.question ILIKE CONCAT('%', :keyword, '%'))
            ORDER BY qa.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM quiz_attempts qa
            INNER JOIN quiz_questions qq ON qa.quiz_id = qq.id
            WHERE qa.user_id = :userId AND qa.status = 'WRONG_BOOK'
            AND (:difficulty IS NULL OR qq.difficulty = :difficulty)
            AND (:knowledgePoint IS NULL OR qq.knowledge_point = :knowledgePoint)
            AND (:repoId IS NULL OR qq.repo_id = :repoId)
            AND (:keyword IS NULL OR qq.question ILIKE CONCAT('%', :keyword, '%'))
            """,
            nativeQuery = true)
    Page<QuizAttempt> findWrongBookWithFilters(
            @Param("userId") Long userId,
            @Param("difficulty") String difficulty,
            @Param("knowledgePoint") String knowledgePoint,
            @Param("repoId") Long repoId,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** 获取用户所有错题本记录（用于统计筛选选项） */
    @Query("SELECT qa FROM QuizAttempt qa WHERE qa.userId = :userId AND qa.status = 'WRONG_BOOK'")
    List<QuizAttempt> findAllWrongBook(@Param("userId") Long userId);

    /** 统计用户答题正确率 */
    @Query("SELECT COUNT(qa) FROM QuizAttempt qa WHERE qa.userId = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(qa) FROM QuizAttempt qa WHERE qa.userId = :userId AND qa.isCorrect = true")
    long countCorrectByUserId(@Param("userId") Long userId);

    /** 按状态统计 */
    @Query("SELECT COUNT(qa) FROM QuizAttempt qa WHERE qa.userId = :userId AND qa.status = :status")
    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    /** 查询用户对某题的最近一次作答 */
    @Query("SELECT qa FROM QuizAttempt qa WHERE qa.userId = :userId AND qa.quizId = :quizId ORDER BY qa.createdAt DESC")
    List<QuizAttempt> findLatestByUserIdAndQuizId(@Param("userId") Long userId, @Param("quizId") Long quizId);
}
