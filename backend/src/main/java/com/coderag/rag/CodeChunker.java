package com.coderag.rag;

import com.coderag.entity.CodeChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码分块器
 * 将代码文件按行数分块，每块附带上下文信息
 */
@Component
public class CodeChunker {

    private static final int DEFAULT_CHUNK_SIZE = 200;
    private static final int OVERLAP_SIZE = 10;

    /**
     * 将单个文件内容分块
     */
    public List<CodeChunk> chunkFile(Long repoId, String filePath, String language, String content) {
        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        if (lines.length <= DEFAULT_CHUNK_SIZE) {
            // 文件较短，整块存储
            CodeChunk chunk = new CodeChunk();
            chunk.setRepoId(repoId);
            chunk.setFilePath(filePath);
            chunk.setLanguage(language);
            chunk.setContent(content);
            chunk.setStartLine(1);
            chunk.setEndLine(lines.length);
            chunks.add(chunk);
            return chunks;
        }

        // 长文件滑动窗口分块
        int start = 0;
        while (start < lines.length) {
            int end = Math.min(start + DEFAULT_CHUNK_SIZE, lines.length);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(lines[i]);
                if (i < end - 1) sb.append("\n");
            }

            CodeChunk chunk = new CodeChunk();
            chunk.setRepoId(repoId);
            chunk.setFilePath(filePath);
            chunk.setLanguage(language);
            chunk.setContent(sb.toString());
            chunk.setStartLine(start + 1);
            chunk.setEndLine(end);
            chunks.add(chunk);

            start += (DEFAULT_CHUNK_SIZE - OVERLAP_SIZE);
            if (start >= lines.length) break;
        }

        return chunks;
    }
}
