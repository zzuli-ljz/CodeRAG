package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 代码仓库实体
 */
@Data
@Entity
@Table(name = "code_repositories")
public class CodeRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(nullable = false, length = 500)
    private String repoUrl;

    @Column(nullable = false, length = 200)
    private String repoName;

    @Column(length = 200)
    private String repoOwner;

    @Column(length = 1000)
    private String description;

    @Column(length = 100)
    private String defaultBranch;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    private Integer fileCount;

    private Long codeLineCount;

    private Long storageBytes;

    @Column(length = 50)
    private String language;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
