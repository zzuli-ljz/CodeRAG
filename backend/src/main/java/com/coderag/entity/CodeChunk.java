package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 代码向量分块实体（对应 pgvector 向量存储）
 */
@Data
@Entity
@Table(name = "code_chunks")
public class CodeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repoId;

    @Column(length = 500)
    private String filePath;

    @Column(length = 100)
    private String language;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private Integer startLine;

    private Integer endLine;

    /**
     * pgvector 向量字段，1024 维（text-embedding-v4 默认维度）
     * 通过原生 SQL 操作，JPA 不直接映射
     */
    @Transient
    private float[] embedding;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
