package com.coderag.service;

import com.coderag.common.cache.CacheService;
import com.coderag.entity.CodeChunk;
import com.coderag.entity.CodeRepository;
import com.coderag.entity.TranslationHistory;
import com.coderag.rag.BailianAiService;
import com.coderag.repository.CodeChunkRepository;
import com.coderag.repository.CodeRepositoryRepository;
import com.coderag.repository.TranslationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 代码翻译服务
 * 支持仓库内文件翻译 + 自由代码片段翻译
 * 优先使用正则/规则检测语言，减少 Token 消耗
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslateService {

    private final BailianAiService aiService;
    private final CodeChunkRepository codeChunkRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final TranslationHistoryRepository translationHistoryRepository;
    private final CacheService cacheService;

    /**
     * 翻译仓库内的指定文件
     */
    public TranslationHistory translateFile(Long userId, Long repoId, String filePath, String targetLang) {
        // 1. 校验仓库存在
        codeRepositoryRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("仓库不存在，repoId=" + repoId));

        // 2. 从已缓存的代码分块中获取文件内容
        List<CodeChunk> chunks = codeChunkRepository.findByRepoId(repoId);
        if (chunks == null || chunks.isEmpty()) {
            throw new RuntimeException("该仓库暂无代码数据，请先完成导入");
        }

        // 按文件路径筛选并拼接
        List<CodeChunk> fileChunks = chunks.stream()
                .filter(c -> filePath.equals(c.getFilePath()))
                .sorted((a, b) -> Integer.compare(
                        a.getStartLine() != null ? a.getStartLine() : 0,
                        b.getStartLine() != null ? b.getStartLine() : 0))
                .toList();

        if (fileChunks.isEmpty()) {
            throw new RuntimeException("未找到文件: " + filePath);
        }

        String sourceCode = fileChunks.stream()
                .map(CodeChunk::getContent)
                .collect(Collectors.joining("\n"));

        // 3. 检测源语言（基于文件扩展名，零 Token 消耗）
        String sourceLang = detectLanguageByPath(filePath);

        // 4. 获取项目上下文（同仓库其他相关文件摘要）
        String projectContext = buildProjectContext(repoId, filePath, chunks);

        // 5. 执行翻译
        return doTranslate(userId, repoId, filePath, sourceCode, sourceLang, targetLang, projectContext);
    }

    /** 自由代码片段最大字符数（防止用户传入超大文本） */
    private static final int MAX_SNIPPET_LENGTH = 8000;

    /**
     * 翻译自由代码片段
     */
    public TranslationHistory translateSnippet(Long userId, String sourceCode, String sourceLang, String targetLang) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new RuntimeException("源代码不能为空");
        }
        if (sourceCode.length() > MAX_SNIPPET_LENGTH) {
            throw new RuntimeException("代码片段过长（最大 " + MAX_SNIPPET_LENGTH + " 字符），请减少代码量或使用文件翻译功能");
        }
        if (sourceLang == null || sourceLang.isBlank()) {
            sourceLang = detectLanguageByCode(sourceCode);
        }
        return doTranslate(userId, null, null, sourceCode, sourceLang, targetLang, null);
    }

    /**
     * 获取翻译历史
     */
    public Page<TranslationHistory> getHistory(Long userId, Long repoId, int page, int size) {
        return translationHistoryRepository.findByUserIdAndRepoIdOrderByCreatedAtDesc(
                userId, repoId, PageRequest.of(page, size));
    }

    // ====== 内部方法 ======

    private TranslationHistory doTranslate(Long userId, Long repoId, String filePath,
                                           String sourceCode, String sourceLang, String targetLang,
                                           String projectContext) {
        // 检查缓存
        String cacheKey = "translate:" + (repoId != null ? repoId : "snippet") + ":"
                + CacheService.chatKey(repoId != null ? repoId : 0L, sourceCode + targetLang);
        java.util.Optional<TranslationHistory> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            log.info("翻译结果命中缓存: sourceLang={}, targetLang={}", sourceLang, targetLang);
            TranslationHistory history = cached.get();
            history.setUserId(userId);
            history.setRepoId(repoId != null ? repoId : 0L);
            history.setId(null); // 新记录
            return translationHistoryRepository.save(history);
        }

        // 截断过长源码（节省 Token）
        String truncatedCode = sourceCode;
        if (sourceCode.length() > 4000) {
            truncatedCode = sourceCode.substring(0, 4000)
                    + "\n\n// ... (源码过长已截断，共 " + sourceCode.length() + " 字符)";
        }

        // 构建 Prompt
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("请将以下 ").append(sourceLang).append(" 代码翻译为 ").append(targetLang).append("。\n\n");

        promptBuilder.append("要求：\n");
        promptBuilder.append("1. 保持完全等价的功能逻辑\n");
        promptBuilder.append("2. 适配目标语言的惯用写法和最佳实践\n");
        promptBuilder.append("3. 保留原注释并翻译为中文\n");
        promptBuilder.append("4. 如果目标语言缺少某些特性，请给出替代方案\n");
        promptBuilder.append("5. 输出格式严格按以下三段，每段以【】标记开头：\n");
        promptBuilder.append("   【翻译代码】\n");
        promptBuilder.append("   完整的目标语言代码\n\n");
        promptBuilder.append("   【差异说明】\n");
        promptBuilder.append("   逐段对比源语言和目标语言的关键差异\n\n");
        promptBuilder.append("   【注意事项】\n");
        promptBuilder.append("   运行时依赖、API 差异、潜在陷阱等\n\n");

        if (projectContext != null && !projectContext.isEmpty()) {
            promptBuilder.append("项目上下文（供参考代码风格）：\n");
            promptBuilder.append(projectContext).append("\n\n");
        }

        promptBuilder.append("源代码（").append(sourceLang).append("）：\n");
        promptBuilder.append("```").append(sourceLang.toLowerCase()).append("\n");
        promptBuilder.append(truncatedCode).append("\n");
        promptBuilder.append("```");

        String systemPrompt = "你是一个资深多语言代码翻译专家，精通 " + sourceLang + " 和 " + targetLang
                + "，擅长生成符合目标语言最佳实践的等价代码。";

        String result = aiService.chat(systemPrompt, promptBuilder.toString());

        // 解析 AI 返回的三段内容
        String translatedCode = extractSection(result, "翻译代码");
        String diffNotes = extractSection(result, "差异说明");
        String caveats = extractSection(result, "注意事项");

        // 保存记录
        TranslationHistory history = new TranslationHistory();
        history.setUserId(userId);
        history.setRepoId(repoId != null ? repoId : 0L);
        history.setSourceFilePath(filePath);
        history.setSourceLang(sourceLang);
        history.setTargetLang(targetLang);
        history.setSourceCode(sourceCode.length() > 2000 ? sourceCode.substring(0, 2000) + "\n...(已截断)" : sourceCode);
        history.setTranslatedCode(translatedCode);
        history.setDiffNotes(diffNotes);
        history.setCaveats(caveats);
        history = translationHistoryRepository.save(history);

        // 缓存（1小时）
        cacheService.put(cacheKey, history, 60);

        return history;
    }

    /**
     * 基于文件扩展名检测语言（零 Token 消耗）
     */
    private String detectLanguageByPath(String filePath) {
        if (filePath == null) return "Unknown";
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".py")) return "Python";
        if (lower.endsWith(".js")) return "JavaScript";
        if (lower.endsWith(".ts")) return "TypeScript";
        if (lower.endsWith(".tsx")) return "TypeScript(React)";
        if (lower.endsWith(".jsx")) return "JavaScript(React)";
        if (lower.endsWith(".go")) return "Go";
        if (lower.endsWith(".rs")) return "Rust";
        if (lower.endsWith(".rb")) return "Ruby";
        if (lower.endsWith(".php")) return "PHP";
        if (lower.endsWith(".swift")) return "Swift";
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) return "Kotlin";
        if (lower.endsWith(".scala")) return "Scala";
        if (lower.endsWith(".c")) return "C";
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx")) return "C++";
        if (lower.endsWith(".h") || lower.endsWith(".hpp")) return "C/C++ Header";
        if (lower.endsWith(".cs")) return "C#";
        if (lower.endsWith(".dart")) return "Dart";
        if (lower.endsWith(".lua")) return "Lua";
        if (lower.endsWith(".r")) return "R";
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) return "Shell";
        if (lower.endsWith(".sql")) return "SQL";
        if (lower.endsWith(".html")) return "HTML";
        if (lower.endsWith(".css") || lower.endsWith(".scss") || lower.endsWith(".less")) return "CSS";
        if (lower.endsWith(".vue")) return "Vue";
        if (lower.endsWith(".xml")) return "XML";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "YAML";
        if (lower.endsWith(".json")) return "JSON";
        if (lower.endsWith(".md")) return "Markdown";
        if (lower.endsWith(".proto")) return "Protobuf";
        if (lower.endsWith(".graphql")) return "GraphQL";
        return "Unknown";
    }

    /**
     * 基于代码特征检测语言（零 Token 消耗，用于自由代码片段）
     */
    private String detectLanguageByCode(String code) {
        if (code == null || code.isBlank()) return "Unknown";
        String trimmed = code.trim();

        if (trimmed.contains("public class ") || trimmed.contains("import java.")
                || trimmed.contains("private ") && trimmed.contains("void ")) return "Java";
        if (trimmed.contains("def ") && trimmed.contains("import ") && !trimmed.contains(";")) return "Python";
        if (trimmed.contains("func ") && (trimmed.contains("package ") || trimmed.contains(":="))) return "Go";
        if (trimmed.contains("fn ") && trimmed.contains("let ") && trimmed.contains("->")) return "Rust";
        if (trimmed.contains("const ") || trimmed.contains("let ") || trimmed.contains("=>")) return "JavaScript";
        if (trimmed.contains("interface ") && trimmed.contains(": ") && !trimmed.contains("class ")) return "TypeScript";
        if (trimmed.contains("<?php")) return "PHP";
        if (trimmed.contains("#include") && trimmed.contains("<")) return "C/C++";
        if (trimmed.contains("require ") || trimmed.contains("def ") && trimmed.contains("end")) return "Ruby";
        if (trimmed.contains("using System") || trimmed.contains("namespace ")) return "C#";
        if (trimmed.contains("fun ") && trimmed.contains("val ")) return "Kotlin";
        if (trimmed.contains("import React") || trimmed.contains("export default")) return "TypeScript(React)";

        return "Unknown";
    }

    /**
     * 构建项目上下文（同仓库其他文件摘要，帮助 AI 适配风格）
     */
    private String buildProjectContext(Long repoId, String excludePath, List<CodeChunk> allChunks) {
        List<String> relatedFiles = allChunks.stream()
                .filter(c -> !excludePath.equals(c.getFilePath()))
                .filter(c -> c.getFilePath() != null && c.getSummary() != null && !c.getSummary().isBlank())
                .map(c -> c.getFilePath() + ": " + c.getSummary())
                .distinct()
                .limit(10)
                .toList();

        if (relatedFiles.isEmpty()) {
            // 无摘要时用文件列表代替
            List<String> fileList = allChunks.stream()
                    .filter(c -> !excludePath.equals(c.getFilePath()))
                    .map(c -> c.getFilePath() + " (" + c.getLanguage() + ")")
                    .distinct()
                    .limit(15)
                    .toList();
            if (!fileList.isEmpty()) {
                return "项目文件列表：\n" + String.join("\n", fileList);
            }
            return "";
        }

        return "项目相关文件摘要：\n" + String.join("\n", relatedFiles);
    }

    /**
     * 从 AI 返回文本中提取指定段落
     */
    private String extractSection(String text, String sectionName) {
        if (text == null || text.isEmpty()) return "";
        String marker = "【" + sectionName + "】";
        int startIdx = text.indexOf(marker);
        if (startIdx < 0) return "";

        int contentStart = startIdx + marker.length();
        String remaining = text.substring(contentStart);

        // 查找下一个段落标记
        String[] nextMarkers = {"【翻译代码】", "【差异说明】", "【注意事项】"};
        int nextIdx = remaining.length();
        for (String m : nextMarkers) {
            if (m.equals(marker)) continue;
            int idx = remaining.indexOf(m);
            if (idx >= 0 && idx < nextIdx) {
                nextIdx = idx;
            }
        }

        return remaining.substring(0, nextIdx).trim();
    }
}
