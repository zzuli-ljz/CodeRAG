package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.LearningPath;
import com.coderag.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 代码学习路径控制器
 */
@RestController
@RequestMapping("/api/learning-path")
@RequiredArgsConstructor
public class LearningPathController {

    private final LearningPathService learningPathService;

    /** 生成学习路径 */
    @PostMapping("/generate/{repoId}")
    public R<LearningPath> generate(@PathVariable Long repoId,
                                    @RequestParam String repoName,
                                    Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(learningPathService.generate(userId, repoId, repoName));
    }

    /** 获取最新学习路径 */
    @GetMapping("/latest/{repoId}")
    public R<LearningPath> getLatest(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LearningPath path = learningPathService.getLatest(userId, repoId);
        if (path == null) {
            return R.fail("暂无学习路径，请先生成");
        }
        return R.ok(path);
    }

    /** 获取历史学习路径 */
    @GetMapping("/history/{repoId}")
    public R<List<LearningPath>> getHistory(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(learningPathService.getHistory(userId, repoId));
    }
}
