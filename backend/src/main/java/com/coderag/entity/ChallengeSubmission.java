package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 编程挑战提交记录
 */
@Data
@Entity
@Table(name = "challenge_submissions")
public class ChallengeSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long challengeId;

    /** 用户提交的代码 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String submittedCode;

    /** AI 评分（0-100） */
    private Integer score;

    /** AI 评语 */
    @Column(columnDefinition = "TEXT")
    private String feedback;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
