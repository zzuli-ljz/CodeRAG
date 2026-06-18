package com.coderag.service;

import com.coderag.common.cache.CacheService;
import com.coderag.entity.ArchitectureAnalysis;
import com.coderag.entity.CodeChunk;
import com.coderag.entity.CodeRepository;
import com.coderag.rag.BailianAiService;
import com.coderag.VectorStore;
import com.coderag.repository.ArchitectureAnalysisRepository;
import com.coderag.repository.CodeChunkRepository;
import com.coderag.repository.CodeRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目架构解析服务
 * 支持多版本历史记录持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchitectureService {

    private final BailianAiService aiService;
    private final CodeChunkRepository codeChunkRepository;
    private final VectorStore vectorStore;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final CacheService cacheService;
    private final ArchitectureAnalysisRepository architectureAnalysisRepository;

    /**
     * AI 自动生成项目架构分析
     * 如果已有分析结果直接返回（节省 token），前端可点「重新分析」强制刷新
     */
    public ArchitectureAnalysis analyzeArchitecture(Long userId, Long repoId, boolean forceRefresh) {
        // 1. 不强制刷新时，优先返回已有结果
        if (!forceRefresh) {
            ArchitectureAnalysis existing = getLatestAnalysis(repoId);
            if (existing != null) {
                log.info("架构分析已有结果，直接返回: repoId={}, round={}", repoId, existing.getRound());
                return existing;
            }
        }

        CodeRepository repo = codeRepositoryRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("仓库不存在，repoId=" + repoId));

        // 获取仓库所有代码分块摘要
        List<CodeChunk> chunks = safeGetChunks(repoId);

        if (chunks == null || chunks.isEmpty()) {
            throw new RuntimeException("该仓库暂无代码数据，请先在「导入仓库」完成导入并等待解析完成");
        }

        // 构建文件列表概要（优先用摘要，减少 token 消耗）
        StringBuilder fileListBuilder = new StringBuilder();
        int totalLen = 0;
        int fileCount = 0;
        for (CodeChunk c : chunks.stream()
                .filter(c -> c.getFilePath() != null)
                .collect(Collectors.toMap(
                        CodeChunk::getFilePath,
                        c -> c,
                        (a, b) -> a))  // 按文件路径去重
                .values()) {
            String line;
            if (c.getSummary() != null && !c.getSummary().isBlank()) {
                line = c.getFilePath() + " (" + c.getLanguage() + ") - " + c.getSummary();
            } else {
                line = c.getFilePath() + " (" + c.getLanguage() + ")";
            }
            if (totalLen + line.length() + 1 > 5000) {
                break;
            }
            if (fileCount > 0) fileListBuilder.append("\n");
            fileListBuilder.append(line);
            totalLen += line.length() + 1;
            fileCount++;
        }
        String fileList = fileListBuilder.toString();

        if (fileList.isEmpty()) {
            throw new RuntimeException("该仓库暂无代码数据，请先在「导入仓库」完成导入并等待解析完成");
        }

        if (fileCount < chunks.stream().map(CodeChunk::getFilePath).distinct().count()) {
            fileList += "\n...(文件列表已截断，共显示 " + fileCount + " 个文件)";
        }

        String prompt = """
                分析以下代码仓库的项目架构，要求：
                1. 整体架构说明（单体/微服务/库/工具等）
                2. 目录结构及各模块职责
                3. 核心模块间依赖关系
                4. 技术栈识别
                5. 入口文件与启动流程
                
                仓库信息：
                名称：%s
                语言：%s
                平台：%s
                
                文件列表：
                %s
                """.formatted(repo.getRepoName(), repo.getLanguage(), repo.getPlatform(), fileList);

        String result = safeCallAi(repoId, prompt);

        // 计算轮次
        int nextRound = computeNextRound(repoId);

        // 持久化新版本
        ArchitectureAnalysis analysis = new ArchitectureAnalysis();
        analysis.setRepoId(repoId);
        analysis.setRound(nextRound);
        analysis.setAnalysisResult(result);
        analysis = safeSave(analysis, repoId);

        // 更新内存缓存（最新结果）
        String cacheKey = CacheService.architectureKey(repoId);
        cacheService.put(cacheKey, result, 120);

        log.info("架构分析完成: repoId={}, round={}", repoId, nextRound);
        return analysis;
    }

    private List<CodeChunk> safeGetChunks(Long repoId) {
        try {
            return codeChunkRepository.findByRepoId(repoId);
        } catch (Exception e) {
            log.error("查询代码分块失败: repoId={}, error={}", repoId, e.getMessage());
            throw new RuntimeException("查询仓库代码数据失败，请确认该仓库已完成导入");
        }
    }

    private String safeCallAi(Long repoId, String prompt) {
        try {
            return aiService.chat("你是资深架构师，擅长分析项目整体架构。", prompt);
        } catch (Exception e) {
            log.error("AI 架构分析调用失败: repoId={}, error={}", repoId, e.getMessage());
            throw new RuntimeException("AI 分析服务调用失败（" + e.getMessage() + "），请检查 API Key 配置或稍后重试");
        }
    }

    private int computeNextRound(Long repoId) {
        try {
            List<ArchitectureAnalysis> existingList = architectureAnalysisRepository.findByRepoIdOrderByCreatedAtDesc(repoId);
            if (!existingList.isEmpty()) {
                return existingList.get(0).getRound() + 1;
            }
        } catch (Exception e) {
            log.warn("查询历史分析记录失败（将使用默认轮次）: repoId={}, error={}", repoId, e.getMessage());
        }
        return 1;
    }

    private ArchitectureAnalysis safeSave(ArchitectureAnalysis analysis, Long repoId) {
        try {
            return architectureAnalysisRepository.save(analysis);
        } catch (Exception e) {
            log.error("保存架构分析结果失败: repoId={}, error={}", repoId, e.getMessage());
            throw new RuntimeException("保存分析结果到数据库失败（" + e.getMessage() + "），请联系管理员检查数据库表结构");
        }
    }

    /**
     * 获取最新架构分析结果
     */
    public ArchitectureAnalysis getLatestAnalysis(Long repoId) {
        return architectureAnalysisRepository.findFirstByRepoIdOrderByCreatedAtDesc(repoId)
                .orElse(null);
    }

    /**
     * 获取所有架构分析历史（分页，支持查看历史版本）
     */
    public Page<ArchitectureAnalysis> getHistory(Long repoId, int page, int size) {
        return architectureAnalysisRepository.findByRepoIdOrderByCreatedAtDesc(repoId, PageRequest.of(page, size));
    }
}
