package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.CodeChallenge;
import com.coderag.entity.ChallengeSubmission;
import com.coderag.service.ChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 编程挑战控制器
 */
@RestController
@RequestMapping("/api/challenge")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    /** 生成编程挑战 */
    @PostMapping("/generate/{repoId}")
    public R<CodeChallenge> generate(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(challengeService.generate(userId, repoId));
    }

    /** 提交答案 */
    @PostMapping("/submit/{challengeId}")
    public R<ChallengeSubmission> submit(@PathVariable Long challengeId,
                                         @RequestBody Map<String, String> body,
                                         Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return R.fail("提交代码不能为空");
        }
        return R.ok(challengeService.submit(userId, challengeId, code));
    }

    /** 获取挑战列表 */
    @GetMapping("/list/{repoId}")
    public R<Page<CodeChallenge>> list(@PathVariable Long repoId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(challengeService.list(userId, repoId, page, size));
    }

    /** 获取单个挑战 */
    @GetMapping("/{challengeId}")
    public R<CodeChallenge> getById(@PathVariable Long challengeId) {
        return R.ok(challengeService.getById(challengeId));
    }

    /** 获取提交历史 */
    @GetMapping("/submissions/{challengeId}")
    public R<List<ChallengeSubmission>> getSubmissions(@PathVariable Long challengeId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(challengeService.getSubmissions(userId, challengeId));
    }
}
