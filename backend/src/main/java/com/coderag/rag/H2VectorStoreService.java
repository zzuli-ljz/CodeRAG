package com.coderag.rag;

import com.coderag.VectorStore;
import com.coderag.common.cache.CacheService;
import com.coderag.entity.CodeChunk;
import com.coderag.repository.CodeChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * H2 本地开发环境的向量存储 Stub
 * H2 不支持 pgvector，仅提供空实现让项目能启动
 * 线上 PostgreSQL 环境不会加载此类
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class H2VectorStoreService implements VectorStore {

    private final CodeChunkRepository codeChunkRepository;
    private final CacheService cacheService;

    public void saveChunkWithEmbedding(CodeChunk chunk, float[] embedding) {
        codeChunkRepository.save(chunk);
        log.debug("[H2模式] 保存代码分块（无向量）: {}", chunk.getFilePath());
    }

    public int saveChunksWithAutoEmbedding(List<CodeChunk> chunks) {
        codeChunkRepository.saveAll(chunks);
        log.info("[H2模式] 批量保存 {} 个代码分块（无向量）", chunks.size());
        return chunks.size();
    }

    public List<CodeChunk> searchSimilar(Long repoId, String queryText, int topK) {
        log.warn("[H2模式] 向量检索不可用，返回空结果。请使用 PostgreSQL + pgvector 获得完整功能。");
        return Collections.emptyList();
    }

    public void deleteByRepoId(Long repoId) {
        codeChunkRepository.deleteByRepoId(repoId);
        cacheService.removeByPrefix("vec:");
    }
}
