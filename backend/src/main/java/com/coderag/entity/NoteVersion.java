package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 笔记版本实体
 * 每次保存笔记时创建一条新版本记录，支持版本历史查看和回滚
 */
@Data
@Entity
@Table(name = "note_versions")
public class NoteVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long snippetId;

    @Column(nullable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(length = 500)
    private String tags;

    /** 版本号，从 1 开始递增 */
    @Column(nullable = false)
    private Integer versionNumber;

    /** 版本标签（用户可选填，如 "初稿"、"最终版"） */
    @Column(length = 100)
    private String versionLabel;

    /** 笔记摘要（取前 100 字符） */
    @Column(length = 200)
    private String summary;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
