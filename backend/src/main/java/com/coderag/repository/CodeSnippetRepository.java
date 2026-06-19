package com.coderag.repository;

import com.coderag.entity.CodeSnippet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeSnippetRepository extends JpaRepository<CodeSnippet, Long> {

    Page<CodeSnippet> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<CodeSnippet> findByUserIdAndRepoIdOrderByCreatedAtDesc(Long userId, Long repoId, Pageable pageable);

    List<CodeSnippet> findByUserId(Long userId);

    long countByUserId(Long userId);

    @Query("SELECT s FROM CodeSnippet s WHERE s.userId = :userId AND " +
           "(:keyword IS NULL OR s.title LIKE %:keyword% OR s.note LIKE %:keyword% OR s.tags LIKE %:keyword%) " +
           "ORDER BY s.createdAt DESC")
    Page<CodeSnippet> searchByUserId(@Param("userId") Long userId,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);

    /** 获取某仓库下用户已收藏的所有片段（不分页） */
    List<CodeSnippet> findByUserIdAndRepoId(Long userId, Long repoId);

    /** 直接更新笔记和标签（绕过 Hibernate dirty check，确保一定写入） */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE CodeSnippet s SET s.note = :note, s.tags = :tags, s.updatedAt = CURRENT_TIMESTAMP WHERE s.id = :id AND s.userId = :userId")
    int updateNoteAndTags(@Param("id") Long id, @Param("userId") Long userId,
                          @Param("note") String note, @Param("tags") String tags);

    /** 检查片段是否属于某用户（不加载实体，避免一级缓存污染） */
    @Query("SELECT COUNT(s) FROM CodeSnippet s WHERE s.id = :id AND s.userId = :userId")
    Integer countByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 直接读取 tags 字段（不加载实体，避免一级缓存） */
    @Query("SELECT s.tags FROM CodeSnippet s WHERE s.id = :id")
    String findTagsById(@Param("id") Long id);
}
