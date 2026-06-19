package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 代码时间线分析记录
 */
@Data
@Entity
@Table(name = "code_timelines")
public class CodeTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(name = "round", nullable = false)
    private Integer round = 1;

    @Column(name = "timeline_data", columnDefinition = "TEXT")
    private String timelineData;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
