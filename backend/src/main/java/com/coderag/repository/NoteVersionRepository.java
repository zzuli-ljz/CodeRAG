package com.coderag.repository;

import com.coderag.entity.NoteVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteVersionRepository extends JpaRepository<NoteVersion, Long> {

    /** 获取某片段的所有笔记版本，按版本号降序 */
    List<NoteVersion> findBySnippetIdAndUserIdOrderByVersionNumberDesc(Long snippetId, Long userId);

    /** 获取某片段的最大版本号 */
    @Query("SELECT COALESCE(MAX(nv.versionNumber), 0) FROM NoteVersion nv WHERE nv.snippetId = :snippetId")
    Integer findMaxVersionNumber(@Param("snippetId") Long snippetId);

    /** 获取某个版本 */
    Optional<NoteVersion> findByIdAndUserId(Long id, Long userId);

    /** 统计某片段的版本数 */
    long countBySnippetId(Long snippetId);
}
