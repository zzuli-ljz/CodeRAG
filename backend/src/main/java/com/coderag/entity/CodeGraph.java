package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 代码图谱分析结果实体（支持多版本历史记录）
 */
@Data
@Entity
@Table(name = "code_graphs", indexes = {
    @Index(columnList = "repo_id")
})
public class CodeGraph {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repoId;

    /** 分析轮次 */
    private Integer round;

    /** 图谱 JSON 数据（nodes + edges） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String graphData;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
