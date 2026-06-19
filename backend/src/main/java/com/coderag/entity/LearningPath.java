package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 代码学习路径实体
 */
@Data
@Entity
@Table(name = "learning_paths")
public class LearningPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long repoId;

    @Column(nullable = false)
    private Integer round = 1;

    /** AI 生成的学习路径内容（Markdown 格式） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String pathContent;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
