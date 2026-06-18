package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 架构分析结果持久化实体（支持多版本历史记录）
 */
@Data
@Entity
@Table(name = "architecture_analyses", indexes = {
    @Index(columnList = "repo_id")
})
public class ArchitectureAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repoId;

    /** 分析轮次（每次分析自增） */
    private Integer round;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String analysisResult;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
