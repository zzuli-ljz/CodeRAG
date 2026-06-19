package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户成就实体
 */
@Data
@Entity
@Table(name = "user_achievements", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "achievement_key"})
})
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 成就标识：FIRST_ASK / STREAK_7 / STREAK_30 / QUIZ_MASTER / COLLECTOR / EXPLORER 等 */
    @Column(nullable = false, length = 50)
    private String achievementKey;

    @Column(nullable = false, length = 100)
    private String achievementName;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String icon;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime earnedAt;
}
