package com.coderag.repository;

import com.coderag.entity.LearningStreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LearningStreakRepository extends JpaRepository<LearningStreak, Long> {

    Optional<LearningStreak> findByUserIdAndDate(Long userId, LocalDate date);

    List<LearningStreak> findByUserIdAndDateBetweenOrderByDateDesc(Long userId, LocalDate start, LocalDate end);

    List<LearningStreak> findByUserIdOrderByDateDesc(Long userId);

    @Query("SELECT COUNT(DISTINCT s.date) FROM LearningStreak s WHERE s.userId = :userId")
    long countDistinctDatesByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT COUNT(*) FROM (" +
           "SELECT date, date - CAST(ROW_NUMBER() OVER (ORDER BY date) AS INTEGER) AS grp " +
           "FROM (SELECT DISTINCT date FROM learning_streaks WHERE user_id = :userId ORDER BY date DESC) t" +
           ") grouped WHERE grp = (SELECT MAX(date) - CAST(ROW_NUMBER() OVER (ORDER BY date) AS INTEGER) " +
           "FROM (SELECT DISTINCT date FROM learning_streaks WHERE user_id = :userId ORDER BY date DESC LIMIT 1) t2)",
           nativeQuery = true)
    Long getCurrentStreak(@Param("userId") Long userId);
}
