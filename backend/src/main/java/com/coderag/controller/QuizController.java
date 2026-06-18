package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.dto.QuizAnswerRequest;
import com.coderag.entity.QuizAttempt;
import com.coderag.entity.QuizQuestion;
import com.coderag.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 智能刷题控制器
 */
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    /**
     * 为仓库生成题目（每次生成新一组）
     */
    @PostMapping("/generate/{repoId}")
    public R<List<QuizQuestion>> generate(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(quizService.generateQuizzes(userId, repoId));
    }

    /**
     * 获取仓库最新一轮生成的题目
     */
    @GetMapping("/latest/{repoId}")
    public R<List<QuizQuestion>> getLatestQuizzes(@PathVariable Long repoId) {
        return R.ok(quizService.getLatestQuizzes(repoId));
    }

    /**
     * 获取仓库所有题目列表
     */
    @GetMapping("/list/{repoId}")
    public R<List<QuizQuestion>> listQuizzes(@PathVariable Long repoId) {
        return R.ok(quizService.getQuizzes(repoId));
    }

    /**
     * 提交作答
     */
    @PostMapping("/answer")
    public R<QuizAttempt> submitAnswer(@Valid @RequestBody QuizAnswerRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(quizService.submitAnswer(userId, request.getQuizId(), request.getUserAnswer()));
    }

    /**
     * 作答历史
     */
    @GetMapping("/attempts")
    public R<Page<QuizAttempt>> getAttempts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(quizService.getAttemptHistory(userId, page, size));
    }
}
