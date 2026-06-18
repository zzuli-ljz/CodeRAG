package com.coderag.service;

import com.coderag.common.cache.CacheService;
import com.coderag.entity.ChatHistory;
import com.coderag.entity.CodeChunk;
import com.coderag.rag.BailianAiService;
import com.coderag.VectorStore;
import com.coderag.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RAG 智能代码问答服务
 * 含问答结果缓存，减少 token 消耗
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final BailianAiService aiService;
    private final VectorStore vectorStore;
    private final ChatHistoryRepository chatHistoryRepository;
    private final QuotaService quotaService;
    private final CacheService cacheService;

    /** 单个代码片段最大字符数（超过则截断） */
    private static final int MAX_CHUNK_LENGTH = 1500;
    /** 上下文总最大字符数 */
    private static final int MAX_CONTEXT_TOTAL = 6000;

    /**
     * RAG 代码问答
     */
    public ChatHistory ask(Long userId, Long repoId, String question) {
        // 1. 配额检查 + 预扣（防止并发超限）
        try {
            quotaService.checkChatQuota(userId);
            quotaService.incrementChatCount(userId);
        } catch (Exception e) {
            log.warn("配额检查失败: userId={}, error={}", userId, e.getMessage());
            throw new RuntimeException("配额检查失败: " + e.getMessage());
        }

        // 2. 检查问答结果缓存
        String cacheKey = CacheService.chatKey(repoId, question);
        Optional<String> cachedAnswer = cacheService.get(cacheKey);
        if (cachedAnswer.isPresent()) {
            log.info("问答结果命中缓存: repoId={}, question={}", repoId, question);
            ChatHistory history = new ChatHistory();
            history.setUserId(userId);
            history.setRepoId(repoId);
            history.setQuestion(question);
            history.setAnswer(cachedAnswer.get());
            history.setSourceSnippets("(缓存结果)");
            return chatHistoryRepository.save(history);
        }

        // 3. 向量检索相关代码片段
        List<CodeChunk> relevantChunks = safeSearchVectors(repoId, question);

        // 4. 构建上下文（带截断，防止 token 爆炸）
        StringBuilder contextBuilder = new StringBuilder();
        int totalLen = 0;
        int includedCount = 0;
        for (CodeChunk chunk : relevantChunks) {
            String content = chunk.getContent();
            // 截断单个过长片段
            if (content.length() > MAX_CHUNK_LENGTH) {
                content = content.substring(0, MAX_CHUNK_LENGTH)
                        + "\n// ... (片段过长已截断，共 " + chunk.getContent().length() + " 字符)";
            }
            String entry = String.format("【文件: %s (行 %d-%d)】\n%s",
                    chunk.getFilePath(), chunk.getStartLine(), chunk.getEndLine(), content);
            if (includedCount > 0) {
                entry = "\n\n---\n\n" + entry;
            }
            // 总长度控制
            if (totalLen + entry.length() > MAX_CONTEXT_TOTAL) {
                log.info("RAG 上下文达到上限，截断: repoId={}, 已包含 {} 个片段", repoId, includedCount);
                break;
            }
            contextBuilder.append(entry);
            totalLen += entry.length();
            includedCount++;
        }
        String context = contextBuilder.toString();

        // 5. 构建 RAG Prompt
        String systemPrompt = """
                你是一个专业的代码分析助手。基于以下代码上下文回答用户的问题。
                要求：
                1. 回答必须基于提供的代码上下文，不要编造不存在的信息
                2. 引用代码时标注来源文件和行号
                3. 如果上下文不足以回答问题，请如实说明
                4. 使用中文回答，代码部分保持原样
                
                代码上下文：
                """ + context;

        // 6. 调用 AI 生成回答
        String answer = safeCallAi(systemPrompt, question);

        // 7. 缓存问答结果
        cacheService.putChatCache(cacheKey, answer);

        // 8. 记录溯源信息
        String sources = relevantChunks.stream()
                .map(c -> c.getFilePath() + ":" + c.getStartLine() + "-" + c.getEndLine())
                .collect(Collectors.joining("; "));

        // 9. 保存历史记录
        ChatHistory history = new ChatHistory();
        history.setUserId(userId);
        history.setRepoId(repoId);
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setSourceSnippets(sources);
        history = chatHistoryRepository.save(history);

        // 配额计数已在入口处预扣，此处不再重复

        return history;
    }

    private List<CodeChunk> safeSearchVectors(Long repoId, String question) {
        List<CodeChunk> relevantChunks;
        try {
            relevantChunks = vectorStore.searchSimilar(repoId, question, 5);
        } catch (RuntimeException e) {
            // 下游已抛出具体错误信息（如免费额度耗尽、API Key 无效等），直接透传
            log.error("向量检索失败: repoId={}, error={}", repoId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("向量检索异常: repoId={}, error={}", repoId, e.getMessage(), e);
            throw new RuntimeException(
                    "代码数据检索失败（技术详情: " + e.getMessage() + "）。\n" +
                    "可能原因：该仓库暂无代码数据或向量索引未建立完成");
        }

        if (relevantChunks == null || relevantChunks.isEmpty()) {
            throw new RuntimeException(
                    "未检索到相关代码片段。\n" +
                    "可能原因：\n" +
                    "1. 该仓库尚未导入代码\n" +
                    "2. 代码解析/向量化还在进行中\n" +
                    "3. 问题与仓库内容无关");
        }
        return relevantChunks;
    }

    private String safeCallAi(String systemPrompt, String question) {
        try {
            return aiService.chat(systemPrompt, question);
        } catch (Exception e) {
            log.error("AI 对话调用失败: {}", e.getMessage(), e);
            throw new RuntimeException(
                    "AI 服务调用失败（" + e.getMessage() + "）。" +
                    "请检查 ARK_API_KEY 是否已配置且额度充足");
        }
    }

    /**
     * 获取问答历史
     */
    public Page<ChatHistory> getChatHistory(Long userId, Long repoId, int page, int size) {
        return chatHistoryRepository.findByUserIdAndRepoIdOrderByCreatedAtDesc(userId, repoId, PageRequest.of(page, size));
    }
}
