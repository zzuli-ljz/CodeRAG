package com.coderag.repository;

import com.coderag.entity.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeChunkRepository extends JpaRepository<CodeChunk, Long> {

    List<CodeChunk> findByRepoId(Long repoId);

    long countByRepoId(Long repoId);

    @Modifying
    @Query("DELETE FROM CodeChunk c WHERE c.repoId = :repoId")
    void deleteByRepoId(@Param("repoId") Long repoId);

    /**
     * pgvector 向量相似度检索（余弦距离）
     * 通过原生 SQL 实现，embedding 为 float[] 向量
     */
    @Query(value = "SELECT id, repo_id, file_path, language, content, summary, start_line, end_line, created_at, " +
            "1 - (embedding <=> :queryVector) AS similarity " +
            "FROM code_chunks WHERE repo_id = :repoId " +
            "ORDER BY embedding <=> :queryVector LIMIT :limit", nativeQuery = true)
    List<Object[]> searchBySimilarity(@Param("repoId") Long repoId,
                                      @Param("queryVector") String queryVector,
                                      @Param("limit") int limit);
}
