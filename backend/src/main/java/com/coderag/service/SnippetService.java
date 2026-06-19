package com.coderag.service;

import com.coderag.entity.CodeSnippet;
import com.coderag.repository.CodeSnippetRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 代码片段收藏服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnippetService {

    private final CodeSnippetRepository snippetRepository;
    private final AchievementService achievementService;
    private final EntityManager entityManager;

    /** 收藏代码片段 */
    @Transactional
    public CodeSnippet save(Long userId, Long repoId, String filePath, String language,
                            String content, String title, String note, String tags,
                            Integer startLine, Integer endLine) {
        CodeSnippet snippet = new CodeSnippet();
        snippet.setUserId(userId);
        snippet.setRepoId(repoId);
        snippet.setFilePath(filePath);
        snippet.setLanguage(language);
        snippet.setContent(content);
        snippet.setTitle(title != null ? title : (filePath != null ? filePath : "未命名片段"));
        snippet.setNote(note);
        snippet.setTags(tags);
        snippet.setStartLine(startLine);
        snippet.setEndLine(endLine);
        CodeSnippet saved = snippetRepository.save(snippet);
        // 触发收藏相关成就
        achievementService.checkAndAwardSnippetAchievements(userId);
        return saved;
    }

    /** 获取单个片段（readOnly 事务确保读到最新数据，避免 Hibernate 缓存旧值） */
    @Transactional(readOnly = true)
    public CodeSnippet getById(Long snippetId, Long userId) {
        CodeSnippet snippet = snippetRepository.findById(snippetId)
                .orElseThrow(() -> new RuntimeException("片段不存在"));
        if (!snippet.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看");
        }
        return snippet;
    }

    /** 更新笔记（使用 JPQL 直接更新，绕过 Hibernate dirty check，确保一定写入数据库） */
    @Transactional
    public CodeSnippet updateNote(Long snippetId, Long userId, String note, String tags) {
        String finalNote = note != null ? note : "";
        log.info("updateNote: snippetId={}, userId={}, note长度={}, tags={}",
                snippetId, userId, finalNote.length(), tags);

        // 先验证权限（通过 JPQL 查询，不加载到一级缓存）
        Integer count = snippetRepository.countByIdAndUserId(snippetId, userId);
        if (count == null || count == 0) {
            // 可能是片段不存在或无权访问，用 findById 获取具体错误信息
            CodeSnippet snippet = snippetRepository.findById(snippetId)
                    .orElseThrow(() -> new RuntimeException("片段不存在"));
            if (!snippet.getUserId().equals(userId)) {
                throw new RuntimeException("无权修改");
            }
        }

        // 使用 JPQL 直接更新，确保一定写入数据库
        String finalTags = tags;
        if (finalTags == null) {
            // 从数据库直接读取当前 tags（避免一级缓存问题）
            finalTags = snippetRepository.findTagsById(snippetId);
        }
        int updated = snippetRepository.updateNoteAndTags(snippetId, userId, finalNote, finalTags);
        log.info("updateNote JPQL 执行结果: updated={}", updated);

        // 关键：清除一级缓存，然后刷新，确保 findById 读到数据库最新值
        entityManager.clear();
        CodeSnippet refreshed = snippetRepository.findById(snippetId)
                .orElseThrow(() -> new RuntimeException("片段在更新后丢失"));
        log.info("updateNote 完成: 数据库note长度={}", refreshed.getNote() != null ? refreshed.getNote().length() : 0);
        return refreshed;
    }

    /** 删除收藏 */
    public void delete(Long snippetId, Long userId) {
        CodeSnippet snippet = snippetRepository.findById(snippetId)
                .orElseThrow(() -> new RuntimeException("片段不存在"));
        if (!snippet.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除");
        }
        snippetRepository.delete(snippet);
    }

    /** 获取收藏列表 */
    public Page<CodeSnippet> list(Long userId, Long repoId, String keyword, int page, int size) {
        if (keyword != null && !keyword.isBlank()) {
            return snippetRepository.searchByUserId(userId, keyword, PageRequest.of(page, size));
        }
        if (repoId != null) {
            return snippetRepository.findByUserIdAndRepoIdOrderByCreatedAtDesc(userId, repoId, PageRequest.of(page, size));
        }
        return snippetRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    /** 获取用户所有标签 */
    public List<String> getUserTags(Long userId) {
        List<CodeSnippet> snippets = snippetRepository.findByUserId(userId);
        Set<String> tagSet = new TreeSet<>();
        for (CodeSnippet s : snippets) {
            if (s.getTags() != null) {
                for (String tag : s.getTags().split(",")) {
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty()) tagSet.add(trimmed);
                }
            }
        }
        return new ArrayList<>(tagSet);
    }

    /** 导出为 Markdown */
    public String exportMarkdown(Long userId) {
        List<CodeSnippet> snippets = snippetRepository.findByUserId(userId);
        StringBuilder sb = new StringBuilder();
        sb.append("# 我的代码片段收藏\n\n");
        sb.append("> 导出时间: ").append(java.time.LocalDateTime.now()).append("\n\n---\n\n");

        for (CodeSnippet s : snippets) {
            sb.append("## ").append(s.getTitle() != null ? s.getTitle() : "未命名").append("\n\n");
            if (s.getFilePath() != null) sb.append("- **文件**: `").append(s.getFilePath()).append("`\n");
            if (s.getLanguage() != null) sb.append("- **语言**: ").append(s.getLanguage()).append("\n");
            if (s.getTags() != null && !s.getTags().isEmpty()) sb.append("- **标签**: ").append(s.getTags()).append("\n");
            if (s.getNote() != null && !s.getNote().isEmpty()) sb.append("\n### 笔记\n\n").append(s.getNote()).append("\n");
            sb.append("\n```").append(s.getLanguage() != null ? s.getLanguage().toLowerCase() : "").append("\n");
            sb.append(s.getContent()).append("\n```\n\n---\n\n");
        }
        return sb.toString();
    }

    public long countByUser(Long userId) {
        return snippetRepository.countByUserId(userId);
    }

    /**
     * 获取某仓库下用户已收藏的片段（只返回匹配所需的关键字段）
     * 用于前端恢复收藏按钮状态
     */
    public List<Map<String, Object>> getCollectedByRepo(Long userId, Long repoId) {
        List<CodeSnippet> snippets = snippetRepository.findByUserIdAndRepoId(userId, repoId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CodeSnippet s : snippets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("filePath", s.getFilePath());
            item.put("startLine", s.getStartLine());
            item.put("endLine", s.getEndLine());
            result.add(item);
        }
        return result;
    }
}
