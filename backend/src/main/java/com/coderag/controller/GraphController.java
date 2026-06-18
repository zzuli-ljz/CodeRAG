package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.CodeGraph;
import com.coderag.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 代码知识图谱控制器
 */
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    /**
     * 构建/获取代码图谱（默认返回已有结果，force=true 时重新构建）
     */
    @PostMapping("/build/{repoId}")
    public R<CodeGraph> build(@PathVariable Long repoId,
                               @RequestParam(defaultValue = "false") boolean force,
                               Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(graphService.buildGraph(userId, repoId, force));
    }

    /**
     * 获取最新图谱
     */
    @GetMapping("/latest/{repoId}")
    public R<CodeGraph> getLatest(@PathVariable Long repoId) {
        return R.ok(graphService.getLatestGraph(repoId));
    }

    /**
     * 获取图谱历史列表
     */
    @GetMapping("/history/{repoId}")
    public R<?> getHistory(@PathVariable Long repoId,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size) {
        return R.ok(graphService.getHistory(repoId, page, size));
    }
}
