package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 编程挑战实体
 */
@Data
@Entity
@Table(name = "code_challenges")
public class CodeChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long repoId;

    @Column(length = 500)
    private String filePath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String challengeDescription;

    /** 函数签名/代码框架（给用户看的） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String codeTemplate;

    /** 原始完整实现（参考答案） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String referenceCode;

    @Column(length = 100)
    private String language;

    @Column(length = 50)
    private String difficulty;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
