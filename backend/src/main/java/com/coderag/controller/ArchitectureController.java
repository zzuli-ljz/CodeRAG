package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.ArchitectureAnalysis;
import com.coderag.service.ArchitectureService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 项目架构解析控制器
 */
@RestController
@RequestMapping("/api/architecture")
@RequiredArgsConstructor
public class ArchitectureController {

    private final ArchitectureService architectureService;

    /**
     * 分析项目架构（默认返回已有结果，force=true 时重新分析）
     */
    @PostMapping("/analyze/{repoId}")
    public R<ArchitectureAnalysis> analyze(@PathVariable Long repoId,
                                            @RequestParam(defaultValue = "false") boolean force,
                                            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(architectureService.analyzeArchitecture(userId, repoId, force));
    }

    /**
     * 获取最新架构分析结果
     */
    @GetMapping("/latest/{repoId}")
    public R<ArchitectureAnalysis> getLatest(@PathVariable Long repoId) {
        return R.ok(architectureService.getLatestAnalysis(repoId));
    }

    /**
     * 获取架构分析历史列表（支持查看历史版本）
     */
    @GetMapping("/history/{repoId}")
    public R<?> getHistory(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(architectureService.getHistory(repoId, page, size));
    }
}
