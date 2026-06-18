package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.dto.VersionCompareRequest;
import com.coderag.entity.VersionComparison;
import com.coderag.service.VersionCompareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 代码版本对比控制器
 */
@RestController
@RequestMapping("/api/version")
@RequiredArgsConstructor
public class VersionCompareController {

    private final VersionCompareService versionCompareService;

    /**
     * 版本对比 + AI 解读
     */
    @PostMapping("/compare")
    public R<VersionComparison> compare(@Valid @RequestBody VersionCompareRequest request,
                                          Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(versionCompareService.compare(userId, request.getRepoId(),
                request.getSourceRef(), request.getTargetRef()));
    }

    /**
     * 对比历史
     */
    @GetMapping("/history/{repoId}")
    public R<Page<VersionComparison>> getHistory(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(versionCompareService.getComparisonHistory(userId, repoId, page, size));
    }
}
