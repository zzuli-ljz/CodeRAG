package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 代码版本对比记录
 */
@Data
@Entity
@Table(name = "version_comparisons")
public class VersionComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long repoId;

    @Column(nullable = false, length = 200)
    private String sourceRef;

    @Column(nullable = false, length = 200)
    private String targetRef;

    @Column(columnDefinition = "TEXT")
    private String diffContent;

    @Column(columnDefinition = "TEXT")
    private String analysisResult;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
