package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 刷题作答记录
 */
@Data
@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long quizId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String userAnswer;

    @Column(nullable = false)
    private Boolean isCorrect;

    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    /**
     * 作答状态：NORMAL(正常)、WRONG_BOOK(错题本)、FAVORITE(收藏)
     */
    @Column(nullable = false, length = 20)
    private String status = "NORMAL";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
