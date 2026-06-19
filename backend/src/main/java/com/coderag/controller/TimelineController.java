package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.CodeTimeline;
import com.coderag.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 代码时间线控制器
 */
@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;

    /**
     * 分析仓库代码演进时间线
     */
    @PostMapping("/analyze/{repoId}")
    public R<CodeTimeline> analyze(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(timelineService.analyze(userId, repoId));
    }

    /**
     * 获取最新时间线
     */
    @GetMapping("/latest/{repoId}")
    public R<CodeTimeline> getLatest(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(timelineService.getLatest(userId, repoId));
    }

    /**
     * 获取历史记录
     */
    @GetMapping("/history/{repoId}")
    public R<Page<CodeTimeline>> getHistory(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(timelineService.getHistory(userId, repoId, page, size));
    }
}
