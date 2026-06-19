package com.coderag.repository;

import com.coderag.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    List<UserAchievement> findByUserIdOrderByEarnedAtDesc(Long userId);

    boolean existsByUserIdAndAchievementKey(Long userId, String achievementKey);

    long countByUserId(Long userId);
}
