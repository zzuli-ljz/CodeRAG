package com.coderag.service;

import com.coderag.entity.CodeChunk;
import com.coderag.entity.LearningPath;
import com.coderag.rag.BailianAiService;
import com.coderag.repository.CodeChunkRepository;
import com.coderag.repository.LearningPathRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 代码学习路径生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathService {

    private final BailianAiService aiService;
    private final CodeChunkRepository codeChunkRepository;
    private final LearningPathRepository learningPathRepository;

    /** 生成学习路径 */
    public LearningPath generate(Long userId, Long repoId, String repoName) {
        // 1. 获取仓库所有代码片段摘要
        List<CodeChunk> chunks = codeChunkRepository.findByRepoId(repoId);
        if (chunks.isEmpty()) {
            throw new RuntimeException("该仓库暂无代码数据，请先导入仓库");
        }

        // 2. 按文件分组，构建项目结构概览
        Map<String, List<CodeChunk>> fileGroups = new LinkedHashMap<>();
        for (CodeChunk c : chunks) {
            String path = c.getFilePath() != null ? c.getFilePath() : "unknown";
            fileGroups.computeIfAbsent(path, k -> new ArrayList<>()).add(c);
        }

        // 3. 构建 AI prompt
        StringBuilder projectOverview = new StringBuilder();
        projectOverview.append("项目名称: ").append(repoName).append("\n\n");
        projectOverview.append("文件结构:\n");
        for (Map.Entry<String, List<CodeChunk>> entry : fileGroups.entrySet()) {
            String path = entry.getKey();
            List<CodeChunk> fileChunks = entry.getValue();
            String lang = fileChunks.get(0).getLanguage();
            projectOverview.append(String.format("- %s (%s, %d个代码片段)\n", path, lang, fileChunks.size()));
        }

        // 添加部分关键代码摘要
        projectOverview.append("\n关键代码摘要:\n");
        int count = 0;
        for (CodeChunk c : chunks) {
            if (c.getSummary() != null && !c.getSummary().isBlank()) {
                projectOverview.append(String.format("- [%s] %s\n", c.getFilePath(), c.getSummary()));
                count++;
                if (count >= 20) break;
            }
        }

        String systemPrompt = """
                你是一个资深的代码导师。基于提供的项目结构和代码摘要，为学习者生成一条渐进式学习路径。
                
                要求：
                1. 将学习路径分为 4-6 个阶段，从入门到深入
                2. 每个阶段包含：阶段名称、学习目标、推荐阅读的文件（按优先级排列）、关键知识点
                3. 说明为什么按照这个顺序学习（依赖关系、难度递进等）
                4. 最后给出一个总结，说明掌握这个项目后能获得什么能力
                5. 使用 Markdown 格式输出，结构清晰
                6. 语言使用中文
                """;

        String userMessage = projectOverview.toString();

        // 4. 调用 AI 生成
        String pathContent = aiService.chat(systemPrompt, userMessage);

        // 5. 保存
        LearningPath learningPath = new LearningPath();
        learningPath.setUserId(userId);
        learningPath.setRepoId(repoId);
        learningPath.setRound(1);
        learningPath.setPathContent(pathContent);
        return learningPathRepository.save(learningPath);
    }

    /** 获取最新学习路径 */
    public LearningPath getLatest(Long userId, Long repoId) {
        return learningPathRepository.findTopByUserIdAndRepoIdOrderByCreatedAtDesc(userId, repoId)
                .orElse(null);
    }

    /** 获取历史学习路径 */
    public List<LearningPath> getHistory(Long userId, Long repoId) {
        return learningPathRepository.findByUserIdAndRepoIdOrderByCreatedAtDesc(userId, repoId);
    }
}
