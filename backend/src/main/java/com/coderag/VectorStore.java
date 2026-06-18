package com.coderag;

import com.coderag.entity.CodeChunk;

import java.util.List;

/**
 * 向量存储统一接口
 * - 线上：VectorStoreService（PostgreSQL + pgvector）
 * - 本地：H2VectorStoreService（H2 Stub）
 */
public interface VectorStore {

    void saveChunkWithEmbedding(CodeChunk chunk, float[] embedding);

    int saveChunksWithAutoEmbedding(List<CodeChunk> chunks);

    List<CodeChunk> searchSimilar(Long repoId, String queryText, int topK);

    void deleteByRepoId(Long repoId);
}
