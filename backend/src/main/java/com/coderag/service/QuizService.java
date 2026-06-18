package com.coderag.service;

import com.coderag.common.cache.CacheService;
import com.coderag.common.constant.RoleConstant;
import com.coderag.entity.*;
import com.coderag.exception.BusinessException;
import com.coderag.rag.BailianAiService;
import com.coderag.VectorStore;
import com.coderag.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 智能刷题服务
 * 支持多轮次题目生成 + 持久化历史
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    private final BailianAiService aiService;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final CodeChunkRepository codeChunkRepository;
    private final UserRepository userRepository;
    private final VectorStore vectorStore;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 单个代码片段最大字符数（超过则截断） */
    private static final int MAX_CHUNK_LENGTH = 1200;
    /** 代码上下文总最大字符数 */
    private static final int MAX_CONTEXT_TOTAL = 5000;

    /**
     * 为仓库生成刷题题目（每次生成新一组）
     */
    @Transactional
    public List<QuizQuestion> generateQuizzes(Long userId, Long repoId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (RoleConstant.USER.equals(user.getRole())) {
            throw new BusinessException("智能刷题为高级用户专属功能，请升级");
        }

        List<CodeChunk> chunks = codeChunkRepository.findByRepoId(repoId);
        if (chunks.isEmpty()) {
            throw new BusinessException("该仓库暂无代码数据，请先完成解析");
        }

        // 取部分复杂代码片段生成题目（带截断，防止 token 爆炸）
        StringBuilder ctxBuilder = new StringBuilder();
        int totalLen = 0;
        int includedCount = 0;
        for (CodeChunk c : chunks.stream()
                .filter(c -> c.getContent().length() > 200)
                .limit(5)
                .toList()) {
            String content = c.getContent();
            if (content.length() > MAX_CHUNK_LENGTH) {
                content = content.substring(0, MAX_CHUNK_LENGTH)
                        + "\n// ... (片段过长已截断，共 " + c.getContent().length() + " 字符)";
            }
            String entry = "【" + c.getFilePath() + " 行" + c.getStartLine() + "-" + c.getEndLine() + "】\n" + content;
            if (includedCount > 0) {
                entry = "\n\n---\n\n" + entry;
            }
            if (totalLen + entry.length() > MAX_CONTEXT_TOTAL) {
                break;
            }
            ctxBuilder.append(entry);
            totalLen += entry.length();
            includedCount++;
        }
        String codeContext = ctxBuilder.toString();

        String prompt = """
                基于以下代码片段，生成 5 道代码理解选择题（单选题），要求：
                1. 每道题包含：question(题目文字)、options(A/B/C/D四个选项)、answer(正确答案A/B/C/D)、explanation(解析)、difficulty(EASY/MEDIUM/HARD)、knowledgePoint
                2. 难度从简单到困难递进
                3. 题目类型：代码逻辑理解、bug发现、优化建议、设计模式识别
                4. 严格按以下 JSON 数组格式返回，不要添加其他任何文字说明：
                
                [{"question":"题目","options":"A.选项1\\nB.选项2\\nC.选项3\\nD.选项4","answer":"A","explanation":"详细解析","difficulty":"EASY","knowledgePoint":"知识点"}]
                
                ⚠️ 关键要求：
                - options 字段中，每个选项之间必须用 \\n（换行符）分隔！绝对不能把所有选项写在一行！
                - 每个 option 必须以 A. B. C. D. 开头，后面跟选项内容
                - 示例："A.使用快速排序\\nB.使用冒泡排序\\nC.使用插入排序\\nD.使用选择排序"
                - 只输出 JSON 数组，不要任何其他文字
                
                代码内容：
                %s
                """.formatted(codeContext);

        String response = aiService.chat("你是编程教育专家，擅长出题和讲解。只输出JSON数组，不要其他内容。", prompt);

        // 解析 JSON 并保存为独立的题目记录
        List<QuizQuestion> savedQuestions = parseAndSaveQuestions(repoId, response, codeContext);
        
        log.info("生成题目完成: repoId={}, count={}", repoId, savedQuestions.size());
        return savedQuestions;
    }

    /**
     * 解析 AI 返回的 JSON，保存为独立题目记录
     */
    private List<QuizQuestion> parseAndSaveQuestions(Long repoId, String jsonResponse, String codeContext) {
        // 尝试提取 JSON 数组（AI 可能返回带 markdown 代码块包裹的 JSON）
        String jsonStr = extractJsonArray(jsonResponse);

        List<Map<String, Object>> parsed;
        try {
            parsed = objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("JSON 解析失败，尝试宽松模式: {}", e.getMessage());
            parsed = tryLenientParse(jsonStr);
        }

        if (parsed == null || parsed.isEmpty()) {
            // 兜底：保存为一条原始记录
            log.warn("无法解析为多题结构，使用兜底存储");
            QuizQuestion fallback = new QuizQuestion();
            fallback.setRepoId(repoId);
            fallback.setQuestion(jsonResponse);
            fallback.setAnswer("由AI动态生成");
            fallback.setDifficulty("MIXED");
            fallback.setCodeSnippet(codeContext);
            quizQuestionRepository.save(fallback);
            return List.of(fallback);
        }

        List<QuizQuestion> result = new ArrayList<>();
        int idx = 1;
        for (Map<String, Object> item : parsed) {
            QuizQuestion q = new QuizQuestion();
            q.setRepoId(repoId);
            q.setQuestion(String.valueOf(item.getOrDefault("question", "")));
            q.setOptions(normalizeOptions(String.valueOf(item.getOrDefault("options", ""))));
            q.setAnswer(String.valueOf(item.getOrDefault("answer", "")));
            q.setExplanation(String.valueOf(item.getOrDefault("explanation", "")));
            q.setDifficulty(String.valueOf(item.getOrDefault("difficulty", "MEDIUM")));
            q.setKnowledgePoint(String.valueOf(item.getOrDefault("knowledgePoint", "")));
            q.setCodeSnippet(codeContext);
            quizQuestionRepository.save(q);
            result.add(q);
            idx++;
        }
        return result;
    }

    /**
     * 标准化选项格式：确保每个选项用换行符分隔
     * 处理 AI 返回选项挤在一行的情况，如 "A.xxx B.xxx C.xxx D.xxx"
     */
    private String normalizeOptions(String raw) {
        if (raw == null || raw.isBlank()) return raw;

        // 如果已经包含换行且能正确拆分，直接返回
        String[] lines = raw.split("\n");
        if (lines.length >= 2) {
            long validCount = 0;
            for (String line : lines) {
                if (line.trim().matches("^[A-D][\\.\\、\\s].+")) {
                    validCount++;
                }
            }
            if (validCount >= 2) return raw;
        }

        // 选项挤在一行：用正则拆分 A/B/C/D 前缀
        StringBuilder normalized = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\s*([A-D])[\\.、]\\s*([^A-D]+?)(?=\\s*[A-D][\\.。]|$)").matcher(raw);
        boolean first = true;
        while (m.find()) {
            if (!first) normalized.append("\n");
            normalized.append(m.group(1)).append(". ").append(m.group(2).trim());
            first = false;
        }

        String result = normalized.toString().trim();
        return result.isEmpty() ? raw : result;
    }

    /**
     * 从可能被 markdown 包裹的响应中提取纯 JSON 字符串
     */
    private String extractJsonArray(String raw) {
        String trimmed = raw.trim();
        // 去掉 markdown 代码块包裹
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    /**
     * 宽松模式尝试解析
     */
    private List<Map<String, Object>> tryLenientParse(String jsonStr) {
        try {
            // 尝试找 [ ... ] 部分
            int start = jsonStr.indexOf('[');
            int end = jsonStr.lastIndexOf(']');
            if (start >= 0 && end > start) {
                String sub = jsonStr.substring(start, end + 1);
                return objectMapper.readValue(sub, new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    /**
     * 获取仓库最新一轮生成的题目列表
     */
    public List<QuizQuestion> getLatestQuizzes(Long repoId) {
        List<QuizQuestion> all = quizQuestionRepository.findByRepoIdOrderByCreatedAtDesc(repoId);
        if (all.isEmpty()) return all;

        // 按生成批次分组（同一批次的 createdAt 时间相近，取最新的批次）
        long baseTime = toMillis(all.get(0).getCreatedAt());
        List<QuizQuestion> latestBatch = new ArrayList<>();
        for (QuizQuestion q : all) {
            if (baseTime - toMillis(q.getCreatedAt()) <= 60000L) {
                latestBatch.add(q);
            } else {
                break;
            }
        }
        return latestBatch;
    }

    private static long toMillis(java.time.LocalDateTime ldt) {
        return java.sql.Timestamp.valueOf(ldt).getTime();
    }

    /**
     * 获取仓库所有题目（不分批次）
     */
    public List<QuizQuestion> getQuizzes(Long repoId) {
        return quizQuestionRepository.findByRepoIdOrderByCreatedAtDesc(repoId);
    }

    /**
     * 提交作答 + 本地判断（不再调用 AI 批改，节省 token）
     * 对于选择题直接比对答案
     */
    public QuizAttempt submitAnswer(Long userId, Long quizId, String userAnswer) {
        QuizQuestion quiz = quizQuestionRepository.findById(quizId)
                .orElseThrow(() -> new BusinessException("题目不存在"));

        // 选择题：比对答案
        boolean isCorrect = false;
        String feedback;

        if (quiz.getOptions() != null && !quiz.getOptions().isBlank()) {
            // 选择题模式
            isCorrect = userAnswer != null && userAnswer.equalsIgnoreCase(quiz.getAnswer().trim());
            feedback = isCorrect
                    ? "✅ 回答正确！\n\n**解析：**\n" + (quiz.getExplanation() != null ? quiz.getExplanation() : "无详细解析")
                    : "❌ 回答错误。\n\n**正确答案：** " + quiz.getAnswer() + "\n\n**解析：**\n" + (quiz.getExplanation() != null ? quiz.getExplanation() : "无详细解析");
        } else {
            // 简答题模式 - 调用 AI 判断
            String prompt = """
                    题目：%s
                    用户答案：%s
                    
                    请判断用户答案是否正确，给出：
                    1. 是否正确（true/false）
                    2. 详细解析和知识点讲解
                    
                    格式：{"isCorrect":true/false,"feedback":"详细解析"}
                    """.formatted(quiz.getQuestion(), userAnswer);

            String aiResponse = aiService.chat("你是编程导师，批改学生的代码题目作答。", prompt);

            isCorrect = aiResponse.toLowerCase().contains("\"iscorrect\":true") ||
                    aiResponse.toLowerCase().contains("\"iscorrect\": true");
            feedback = aiResponse;
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.setUserId(userId);
        attempt.setQuizId(quizId);
        attempt.setUserAnswer(userAnswer);
        attempt.setIsCorrect(isCorrect);
        attempt.setAiFeedback(feedback);
        return quizAttemptRepository.save(attempt);
    }

    /**
     * 获取作答历史
     */
    public Page<QuizAttempt> getAttemptHistory(Long userId, int page, int size) {
        return quizAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }
}
