package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.dto.QuizAnswerRequest;
import com.coderag.dto.QuizAttemptDTO;
import com.coderag.entity.QuizAttempt;
import com.coderag.entity.QuizQuestion;
import com.coderag.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public R<Page<QuizAttemptDTO>> getAttempts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(quizService.getAttemptHistory(userId, page, size));
    }

    /**
     * 错题本（支持多条件筛选）
     */
    @GetMapping("/wrong-book")
    public R<Page<QuizAttemptDTO>> getWrongBook(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String knowledgePoint,
            @RequestParam(required = false) Long repoId,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(quizService.getWrongBook(userId, page, size, difficulty, knowledgePoint, repoId, keyword));
    }

    /**
     * 错题本筛选选项（难度、知识点、仓库列表）
     */
    @GetMapping("/wrong-book/filters")
    public R<Map<String, Object>> getWrongBookFilters(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(quizService.getWrongBookFilters(userId));
    }

    /**
     * 收藏列表
     */
    @GetMapping("/favorites")
    public R<Page<QuizAttemptDTO>> getFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(quizService.getFavorites(userId, page, size));
    }

    /**
     * 切换题目状态（错题本/收藏/取消）
     */
    @PutMapping("/attempt/{attemptId}/status")
    public R<QuizAttempt> toggleStatus(
            @PathVariable Long attemptId,
            @RequestParam String status,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(quizService.toggleStatus(userId, attemptId, status));
    }

    /**
     * 答题统计
     */
    @GetMapping("/stats")
    public R<Map<String, Object>> getStats(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(quizService.getQuizStats(userId));
    }

    /**
     * 获取用户对某题的最新作答状态
     */
    @GetMapping("/status/{quizId}")
    public R<Map<String, Object>> getQuizStatus(@PathVariable Long quizId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(quizService.getQuizStatus(userId, quizId));
    }
}
