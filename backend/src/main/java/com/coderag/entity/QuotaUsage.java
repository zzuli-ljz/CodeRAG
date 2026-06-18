package com.coderag.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户每日配额使用记录
 */
@Data
@Entity
@Table(name = "quota_usages", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"userId", "date"})
})
public class QuotaUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer importCount = 0;

    @Column(nullable = false)
    private Integer chatCount = 0;

    private Long storageUsedBytes = 0L;
}
