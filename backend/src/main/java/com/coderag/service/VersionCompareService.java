package com.coderag.service;

import com.coderag.adapter.RepoDataAdapter;
import com.coderag.common.cache.CacheService;
import com.coderag.entity.CodeRepository;
import com.coderag.entity.VersionComparison;
import com.coderag.exception.BusinessException;
import com.coderag.rag.BailianAiService;
import com.coderag.repository.CodeRepositoryRepository;
import com.coderag.repository.VersionComparisonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 代码版本智能对比服务
 * 含对比结果缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionCompareService {

    private final BailianAiService aiService;
    private final VersionComparisonRepository versionComparisonRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final CacheService cacheService;
    private final List<RepoDataAdapter> adapters;

    /**
     * 代码版本对比 + AI 解读
     */
    public VersionComparison compare(Long userId, Long repoId, String sourceRef, String targetRef) {
        CodeRepository repo = codeRepositoryRepository.findById(repoId)
                .orElseThrow(() -> new BusinessException("仓库不存在"));

        // 检查缓存
        String cacheKey = "diff:" + repoId + ":" + sourceRef + "..." + targetRef;
        Optional<VersionComparison> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            log.info("版本对比命中缓存: repoId={}", repoId);
            return cached.get();
        }

        // 获取适配器
        RepoDataAdapter adapter = adapters.stream()
                .filter(a -> a.supports(repo.getRepoUrl()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的平台"));

        // 获取 diff
        String diffContent = adapter.fetchDiff(repo.getRepoUrl(), sourceRef, targetRef);

        // AI 解读
        String prompt = """
                分析以下代码变更，要求：
                1. 改动概述：新增了什么功能/修复了什么问题
                2. 关键改动逻辑解读
                3. 潜在风险点提示
                4. 代码质量评价
                
                源分支/commit: %s
                目标分支/commit: %s
                
                Diff 内容：
                %s
                """.formatted(sourceRef, targetRef, diffContent);

        String analysisResult = aiService.chat("你是资深代码审查专家，擅长分析代码变更。", prompt);

        // 保存记录
        VersionComparison comparison = new VersionComparison();
        comparison.setUserId(userId);
        comparison.setRepoId(repoId);
        comparison.setSourceRef(sourceRef);
        comparison.setTargetRef(targetRef);
        comparison.setDiffContent(diffContent);
        comparison.setAnalysisResult(analysisResult);
        comparison = versionComparisonRepository.save(comparison);

        // 缓存对比结果
        cacheService.put(cacheKey, comparison, 120);

        return comparison;
    }

    /**
     * 获取对比历史
     */
    public Page<VersionComparison> getComparisonHistory(Long userId, Long repoId, int page, int size) {
        return versionComparisonRepository.findByUserIdAndRepoIdOrderByCreatedAtDesc(userId, repoId, PageRequest.of(page, size));
    }
}
