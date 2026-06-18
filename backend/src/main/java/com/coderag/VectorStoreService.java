package com.coderag;

import com.coderag.common.cache.CacheService;
import com.coderag.entity.CodeChunk;
import com.coderag.exception.VectorDimensionMismatchException;
import com.coderag.rag.BailianAiService;
import com.coderag.repository.CodeChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量存储服务（PostgreSQL + pgvector 环境）
 * - 向量维度：1024（text-embedding-v4）
 * - 检索方式：余弦相似度（<=> 算子）
 * - 含向量缓存 + 批量 embedding，减少 API 调用
 *
 * 仅在非 dev profile（线上 PostgreSQL）下激活
 */
@Slf4j
@Service
@Profile("!dev")
@RequiredArgsConstructor
public class VectorStoreService implements VectorStore {

    private final JdbcTemplate jdbcTemplate;
    private final CodeChunkRepository codeChunkRepository;
    private final BailianAiService bailianAiService;
    private final CacheService cacheService;

    @Transactional
    public void saveChunkWithEmbedding(CodeChunk chunk, float[] embedding) {
        CodeChunk saved = codeChunkRepository.save(chunk);

        String vectorStr = arrayToVectorString(embedding);
        jdbcTemplate.update(
                "UPDATE code_chunks SET embedding = ?::vector WHERE id = ?",
                vectorStr, saved.getId()
        );
    }

    @Transactional
    public void saveBatchWithEmbedding(List<CodeChunk> chunks, List<float[]> embeddings) {
        for (int i = 0; i < chunks.size(); i++) {
            saveChunkWithEmbedding(chunks.get(i), embeddings.get(i));
            if ((i + 1) % 50 == 0) {
                log.info("已保存 {}/{} 个代码向量分块", i + 1, chunks.size());
            }
        }
    }

    /**
     * Embedding API 最大输入长度（text-embedding-v2/v3 的 input 长度上限）
     */
    private static final int EMBEDDING_MAX_INPUT_LENGTH = 2000;

    public int saveChunksWithAutoEmbedding(List<CodeChunk> chunks) {
        // 过滤空内容 + 截断超长内容（Embedding API 要求 1 <= length <= 2048）
        List<CodeChunk> validChunks = new ArrayList<>();
        List<String> embedTexts = new ArrayList<>();
        int truncatedCount = 0;
        int emptyCount = 0;

        for (CodeChunk c : chunks) {
            String content = c.getContent();
            if (content == null || content.isBlank()) {
                emptyCount++;
                continue;
            }
            // 截断超长内容（仅用于向量化，原始 chunk 存完整内容）
            if (content.length() > EMBEDDING_MAX_INPUT_LENGTH) {
                truncatedCount++;
                embedTexts.add(content.substring(0, EMBEDDING_MAX_INPUT_LENGTH));
            } else {
                embedTexts.add(content);
            }
            validChunks.add(c);
        }

        if (validChunks.isEmpty()) {
            log.warn("saveChunksWithAutoEmbedding: 所有 chunk 内容为空，跳过向量化");
            return 0;
        }

        if (emptyCount > 0 || truncatedCount > 0) {
            log.info("saveChunksWithAutoEmbedding: 空内容{}个, 超长截断{}个, 有效{}个",
                    emptyCount, truncatedCount, validChunks.size());
        }

        // 批量 embedding
        List<float[]> embeddings = bailianAiService.embedBatch(embedTexts);
        saveBatchWithEmbedding(validChunks, embeddings);
        return validChunks.size();
    }

    /**
     * pgvector 余弦距离检索（<=> 算子，值越小越相似）
     */
    public List<CodeChunk> searchSimilar(Long repoId, String queryText, int topK) {
        float[] queryVector = bailianAiService.embed(queryText);
        String vectorStr = arrayToVectorString(queryVector);

        String sql = "SELECT id, repo_id, file_path, language, content, summary, start_line, end_line, created_at " +
                "FROM code_chunks WHERE repo_id = ? " +
                "ORDER BY embedding <=> ?::vector LIMIT ?";

        try {
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        CodeChunk chunk = new CodeChunk();
                        chunk.setId(rs.getLong("id"));
                        chunk.setRepoId(rs.getLong("repo_id"));
                        chunk.setFilePath(rs.getString("file_path"));
                        chunk.setLanguage(rs.getString("language"));
                        chunk.setContent(rs.getString("content"));
                        chunk.setSummary(rs.getString("summary"));
                        chunk.setStartLine(rs.getInt("start_line"));
                        chunk.setEndLine(rs.getInt("end_line"));
                        return chunk;
                    },
                    repoId, vectorStr, topK
            );
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("different vector dimensions") || msg.contains("dimensions")) {
                log.error("向量维度不匹配: repoId={}, 查询维度={}, 错误={}", repoId, queryVector.length, msg);
                throw new VectorDimensionMismatchException(
                        "向量维度不匹配：该仓库的代码数据是旧版本模型生成的，与当前AI模型维度不同。" +
                                "请在「我的仓库」页面点击「重新导入」该仓库即可自动修复。");
            }
            throw e;
        }
    }

    @Transactional
    public void deleteByRepoId(Long repoId) {
        codeChunkRepository.deleteByRepoId(repoId);
        cacheService.removeByPrefix("vec:");
    }

    private String arrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
