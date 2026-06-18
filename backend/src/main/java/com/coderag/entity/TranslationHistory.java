package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 代码翻译历史记录实体
 */
@Data
@Entity
@Table(name = "translation_histories", indexes = {
    @Index(columnList = "repo_id"),
    @Index(columnList = "user_id")
})
public class TranslationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long repoId;

    /** 源文件路径 */
    @Column(length = 500)
    private String sourceFilePath;

    /** 源语言 */
    @Column(nullable = false, length = 30)
    private String sourceLang;

    /** 目标语言 */
    @Column(nullable = false, length = 30)
    private String targetLang;

    /** 源代码（截断存储，最多5000字符） */
    @Column(columnDefinition = "TEXT")
    private String sourceCode;

    /** 翻译结果 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String translatedCode;

    /** 差异说明 */
    @Column(columnDefinition = "TEXT")
    private String diffNotes;

    /** 注意事项 */
    @Column(columnDefinition = "TEXT")
    private String caveats;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
