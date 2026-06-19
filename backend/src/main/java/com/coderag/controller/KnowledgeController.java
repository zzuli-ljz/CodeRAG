package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.CodeChunk;
import com.coderag.entity.CodeRepository;
import com.coderag.exception.BusinessException;
import com.coderag.repository.CodeChunkRepository;
import com.coderag.repository.CodeRepositoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库预览控制器
 * 查看已向量化的文档片段
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final CodeChunkRepository codeChunkRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;

    /**
     * 按仓库分页查询代码片段列表
     */
    @GetMapping("/chunks/{repoId}")
    public R<Page<CodeChunk>> listChunks(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String filePath) {
        // 简单分页：先查全部再内存分页（数据量不大时可行）
        List<CodeChunk> allChunks = codeChunkRepository.findByRepoId(repoId);

        // 过滤
        if (language != null && !language.isBlank()) {
            allChunks = allChunks.stream()
                    .filter(c -> language.equalsIgnoreCase(c.getLanguage()))
                    .collect(Collectors.toList());
        }
        if (filePath != null && !filePath.isBlank()) {
            allChunks = allChunks.stream()
                    .filter(c -> c.getFilePath() != null && c.getFilePath().contains(filePath))
                    .collect(Collectors.toList());
        }

        int total = allChunks.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, total);

        List<CodeChunk> pageContent;
        if (fromIndex >= total) {
            pageContent = Collections.emptyList();
        } else {
            pageContent = allChunks.subList(fromIndex, toIndex);
        }

        // 构造简易分页对象
        Page<CodeChunk> result = new org.springframework.data.domain.PageImpl<>(
                pageContent, PageRequest.of(page, size), total);
        return R.ok(result);
    }

    /**
     * 获取仓库知识库概览（按文件路径分组统计）
     */
    @GetMapping("/overview/{repoId}")
    public R<Map<String, Object>> getOverview(@PathVariable Long repoId) {
        CodeRepository repo = codeRepositoryRepository.findById(repoId)
                .orElseThrow(() -> new BusinessException("仓库不存在"));

        List<CodeChunk> chunks = codeChunkRepository.findByRepoId(repoId);

        // 按语言统计
        Map<String, Long> langStats = chunks.stream()
                .filter(c -> c.getLanguage() != null)
                .collect(Collectors.groupingBy(CodeChunk::getLanguage, Collectors.counting()));

        // 按文件路径分组
        Map<String, List<Map<String, Object>>> fileGroups = new LinkedHashMap<>();
        for (CodeChunk c : chunks) {
            String path = c.getFilePath() != null ? c.getFilePath() : "未知文件";
            fileGroups.computeIfAbsent(path, k -> new ArrayList<>())
                    .add(Map.of(
                            "id", c.getId(),
                            "startLine", c.getStartLine() != null ? c.getStartLine() : 0,
                            "endLine", c.getEndLine() != null ? c.getEndLine() : 0,
                            "language", c.getLanguage() != null ? c.getLanguage() : "",
                            "summary", c.getSummary() != null ? c.getSummary() : ""
                    ));
        }

        Map<String, Object> overview = new HashMap<>();
        overview.put("repoId", repoId);
        overview.put("repoName", repo.getRepoName());
        overview.put("totalChunks", chunks.size());
        overview.put("totalFiles", fileGroups.size());
        overview.put("languageStats", langStats);
        overview.put("fileGroups", fileGroups.entrySet().stream()
                .map(e -> Map.of(
                        "filePath", (Object) e.getKey(),
                        "chunkCount", e.getValue().size(),
                        "chunks", e.getValue()
                ))
                .collect(Collectors.toList()));

        return R.ok(overview);
    }

    /**
     * 获取单个代码片段详情
     */
    @GetMapping("/chunk/{chunkId}")
    public R<CodeChunk> getChunkDetail(@PathVariable Long chunkId) {
        CodeChunk chunk = codeChunkRepository.findById(chunkId)
                .orElseThrow(() -> new BusinessException("代码片段不存在"));
        return R.ok(chunk);
    }

    /**
     * 获取仓库所有文件路径列表（用于筛选）
     */
    @GetMapping("/files/{repoId}")
    public R<List<String>> listFiles(@PathVariable Long repoId) {
        List<CodeChunk> chunks = codeChunkRepository.findByRepoId(repoId);
        List<String> files = chunks.stream()
                .map(CodeChunk::getFilePath)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return R.ok(files);
    }

    /**
     * 获取仓库所有语言列表（用于筛选）
     */
    @GetMapping("/languages/{repoId}")
    public R<List<String>> listLanguages(@PathVariable Long repoId) {
        List<CodeChunk> chunks = codeChunkRepository.findByRepoId(repoId);
        List<String> languages = chunks.stream()
                .map(CodeChunk::getLanguage)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return R.ok(languages);
    }
}
