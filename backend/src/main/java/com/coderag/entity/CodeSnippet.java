package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 代码片段收藏实体
 */
@Data
@Entity
@Table(name = "code_snippets")
public class CodeSnippet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long repoId;

    @Column(length = 500)
    private String filePath;

    @Column(length = 100)
    private String language;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(length = 500)
    private String tags;

    private Integer startLine;

    private Integer endLine;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
