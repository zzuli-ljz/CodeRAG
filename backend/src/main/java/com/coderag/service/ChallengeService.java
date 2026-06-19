package com.coderag.service;

import com.coderag.entity.CodeChallenge;
import com.coderag.entity.CodeChunk;
import com.coderag.entity.ChallengeSubmission;
import com.coderag.rag.BailianAiService;
import com.coderag.repository.CodeChallengeRepository;
import com.coderag.repository.CodeChunkRepository;
import com.coderag.repository.ChallengeSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * 编程挑战生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final BailianAiService aiService;
    private final CodeChunkRepository codeChunkRepository;
    private final CodeChallengeRepository challengeRepository;
    private final ChallengeSubmissionRepository submissionRepository;

    private final Random random = new Random();

    /** 生成编程挑战 */
    public CodeChallenge generate(Long userId, Long repoId) {
        List<CodeChunk> chunks = codeChunkRepository.findByRepoId(repoId);
        if (chunks.isEmpty()) {
            throw new RuntimeException("该仓库暂无代码数据");
        }

        // 随机选取一个有足够代码量的片段作为挑战素材
        List<CodeChunk> candidates = chunks.stream()
                .filter(c -> c.getContent() != null && c.getContent().length() > 200)
                .toList();
        if (candidates.isEmpty()) {
            throw new RuntimeException("未找到足够长的代码片段来生成挑战");
        }

        CodeChunk selected = candidates.get(random.nextInt(candidates.size()));

        String systemPrompt = """
                你是一个编程教学专家。基于提供的代码片段，生成一个编程挑战。
                
                要求：
                1. 分析代码片段的核心逻辑
                2. 提取函数签名或关键结构作为代码模板（保留函数名、参数、注释）
                3. 隐藏实现细节，用 TODO 注释标记需要用户补全的部分
                4. 生成挑战描述，说明需要实现什么功能
                5. 保留原始代码作为参考答案
                6. 评估难度（easy/medium/hard）
                
                请按以下 JSON 格式返回（不要包含其他内容）：
                {
                  "description": "挑战描述文字",
                  "template": "代码模板（含TODO标记）",
                  "reference": "原始完整代码",
                  "difficulty": "easy/medium/hard"
                }
                """;

        String userMessage = String.format(
                "文件: %s\n语言: %s\n代码:\n```\n%s\n```",
                selected.getFilePath(),
                selected.getLanguage(),
                selected.getContent()
        );

        String aiResponse = aiService.chat(systemPrompt, userMessage);

        // 解析 AI 返回的 JSON
        String description = extractJsonField(aiResponse, "description");
        String template = extractJsonField(aiResponse, "template");
        String reference = extractJsonField(aiResponse, "reference");
        String difficulty = extractJsonField(aiResponse, "difficulty");

        if (description.isEmpty()) description = "请根据代码模板补全实现";
        if (template.isEmpty()) template = selected.getContent();
        if (reference.isEmpty()) reference = selected.getContent();
        if (difficulty.isEmpty()) difficulty = "medium";

        CodeChallenge challenge = new CodeChallenge();
        challenge.setUserId(userId);
        challenge.setRepoId(repoId);
        challenge.setFilePath(selected.getFilePath());
        challenge.setChallengeDescription(description);
        challenge.setCodeTemplate(template);
        challenge.setReferenceCode(reference);
        challenge.setLanguage(selected.getLanguage());
        challenge.setDifficulty(difficulty);
        return challengeRepository.save(challenge);
    }

    /** 提交挑战答案并评分 */
    public ChallengeSubmission submit(Long userId, Long challengeId, String submittedCode) {
        CodeChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RuntimeException("挑战不存在"));

        String systemPrompt = """
                你是一个严格的代码评审专家。对比用户的提交代码和参考答案，给出评分和反馈。
                
                评分标准（满分100）：
                - 功能正确性（50分）：是否实现了预期功能
                - 代码质量（25分）：可读性、命名规范、注释
                - 效率（15分）：算法复杂度、性能
                - 健壮性（10分）：边界处理、错误处理
                
                请按以下 JSON 格式返回：
                {
                  "score": 85,
                  "feedback": "详细的评语，包括优点和需要改进的地方"
                }
                """;

        String userMessage = String.format(
                "题目描述：%s\n\n参考答案：\n```\n%s\n```\n\n用户提交：\n```\n%s\n```",
                challenge.getChallengeDescription(),
                challenge.getReferenceCode(),
                submittedCode
        );

        String aiResponse = aiService.chat(systemPrompt, userMessage);

        int score = 0;
        String feedback = "";
        try {
            score = Integer.parseInt(extractJsonField(aiResponse, "score"));
        } catch (NumberFormatException e) {
            score = 60;
        }
        feedback = extractJsonField(aiResponse, "feedback");
        if (feedback.isEmpty()) feedback = aiResponse;

        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setUserId(userId);
        submission.setChallengeId(challengeId);
        submission.setSubmittedCode(submittedCode);
        submission.setScore(Math.min(100, Math.max(0, score)));
        submission.setFeedback(feedback);
        return submissionRepository.save(submission);
    }

    /** 获取挑战列表 */
    public Page<CodeChallenge> list(Long userId, Long repoId, int page, int size) {
        return challengeRepository.findByUserIdAndRepoIdOrderByCreatedAtDesc(userId, repoId, PageRequest.of(page, size));
    }

    /** 获取提交历史 */
    public List<ChallengeSubmission> getSubmissions(Long userId, Long challengeId) {
        return submissionRepository.findByUserIdAndChallengeIdOrderByCreatedAtDesc(userId, challengeId);
    }

    /** 获取单个挑战 */
    public CodeChallenge getById(Long challengeId) {
        return challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RuntimeException("挑战不存在"));
    }

    private String extractJsonField(String json, String field) {
        try {
            String pattern = "\"" + field + "\"\\s*:\\s*\"";
            int start = json.indexOf(pattern);
            if (start == -1) {
                pattern = "\"" + field + "\"\\s*:\\s*'";
                start = json.indexOf(pattern);
            }
            if (start == -1) return "";

            start = json.indexOf("\"", start + pattern.length() - 1);
            if (start == -1) return "";
            start++;

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    if (next == '"') { sb.append('"'); i++; }
                    else if (next == 'n') { sb.append('\n'); i++; }
                    else if (next == 't') { sb.append('\t'); i++; }
                    else if (next == '\\') { sb.append('\\'); i++; }
                    else { sb.append(c); }
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
