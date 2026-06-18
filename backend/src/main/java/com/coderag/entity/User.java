package com.coderag.entity;

import com.coderag.common.constant.RoleConstant;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "username"),
    @UniqueConstraint(columnNames = "email")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role = RoleConstant.USER;

    @Column(length = 500)
    private String avatar;

    /** 是否被封禁 */
    @Column(nullable = false)
    private Boolean banned = false;

    /** 自定义每日导入配额上限（null 表示使用角色默认配额） */
    private Integer customImportLimit;

    /** 自定义每日 AI 问答配额上限（null 表示使用角色默认配额） */
    private Integer customChatLimit;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
