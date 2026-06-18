package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.dto.TranslateRequest;
import com.coderag.entity.TranslationHistory;
import com.coderag.service.TranslateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 代码翻译控制器
 */
@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final TranslateService translateService;

    /**
     * 翻译仓库内指定文件
     */
    @PostMapping("/file")
    public R<TranslationHistory> translateFile(@Valid @RequestBody TranslateRequest request,
                                                Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(translateService.translateFile(
                userId, request.getRepoId(), request.getFilePath(), request.getTargetLang()));
    }

    /**
     * 翻译自由代码片段
     */
    @PostMapping("/snippet")
    public R<TranslationHistory> translateSnippet(@Valid @RequestBody TranslateRequest request,
                                                   Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(translateService.translateSnippet(
                userId, request.getSourceCode(), request.getSourceLang(), request.getTargetLang()));
    }

    /**
     * 获取翻译历史
     */
    @GetMapping("/history/{repoId}")
    public R<?> getHistory(@PathVariable Long repoId,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(translateService.getHistory(userId, repoId, page, size));
    }
}
