package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习打卡记录实体
 */
@Data
@Entity
@Table(name = "learning_streaks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "date"})
})
public class LearningStreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    /** 当日学习活动次数（问答+刷题+收藏等） */
    @Column(nullable = false)
    private Integer activityCount = 1;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
