package com.coderag.service;

import com.coderag.entity.CodeSnippet;
import com.coderag.entity.NoteVersion;
import com.coderag.repository.CodeSnippetRepository;
import com.coderag.repository.NoteVersionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 笔记版本服务
 * 每次保存笔记时创建新版本，支持版本历史查看和回滚
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteVersionService {

    private final NoteVersionRepository noteVersionRepository;
    private final CodeSnippetRepository snippetRepository;
    private final EntityManager entityManager;

    /**
     * 保存笔记（创建新版本）
     * 1. 创建 NoteVersion 记录
     * 2. 更新 CodeSnippet.note 为最新内容（用于列表展示）
     */
    @Transactional
    public NoteVersion saveVersion(Long snippetId, Long userId, String note, String tags, String versionLabel) {
        // 验证权限
        CodeSnippet snippet = snippetRepository.findById(snippetId)
                .orElseThrow(() -> new RuntimeException("片段不存在"));
        if (!snippet.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改");
        }

        // 计算下一个版本号
        Integer maxVersion = noteVersionRepository.findMaxVersionNumber(snippetId);
        int nextVersion = (maxVersion != null ? maxVersion : 0) + 1;

        // 创建版本记录
        NoteVersion nv = new NoteVersion();
        nv.setSnippetId(snippetId);
        nv.setUserId(userId);
        nv.setNote(note);
        nv.setTags(tags);
        nv.setVersionNumber(nextVersion);
        nv.setVersionLabel(versionLabel);
        nv.setSummary(buildSummary(note));

        NoteVersion saved = noteVersionRepository.save(nv);
        log.info("笔记版本已创建: snippetId={}, version={}, note长度={}",
                snippetId, nextVersion, note != null ? note.length() : 0);

        // 同时更新 CodeSnippet.note 为最新内容（用于列表预览）
        String finalNote = note != null ? note : "";
        String finalTags = tags != null ? tags : snippet.getTags();
        snippetRepository.updateNoteAndTags(snippetId, userId, finalNote, finalTags);
        entityManager.clear();

        return saved;
    }

    /** 获取某片段的所有笔记版本 */
    public List<NoteVersion> getVersions(Long snippetId, Long userId) {
        // 先验证权限
        CodeSnippet snippet = snippetRepository.findById(snippetId)
                .orElseThrow(() -> new RuntimeException("片段不存在"));
        if (!snippet.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看");
        }
        return noteVersionRepository.findBySnippetIdAndUserIdOrderByVersionNumberDesc(snippetId, userId);
    }

    /** 获取某个版本详情 */
    public NoteVersion getVersion(Long versionId, Long userId) {
        return noteVersionRepository.findByIdAndUserId(versionId, userId)
                .orElseThrow(() -> new RuntimeException("版本不存在或无权访问"));
    }

    /** 回滚到指定版本（将 CodeSnippet.note 恢复为该版本内容） */
    @Transactional
    public NoteVersion rollbackToVersion(Long versionId, Long userId) {
        NoteVersion nv = noteVersionRepository.findByIdAndUserId(versionId, userId)
                .orElseThrow(() -> new RuntimeException("版本不存在或无权访问"));

        // 更新 CodeSnippet.note
        String note = nv.getNote() != null ? nv.getNote() : "";
        String tags = nv.getTags();
        snippetRepository.updateNoteAndTags(nv.getSnippetId(), userId, note, tags);
        entityManager.clear();

        log.info("笔记已回滚到版本 v{}: snippetId={}", nv.getVersionNumber(), nv.getSnippetId());
        return nv;
    }

    /** 删除某个版本 */
    @Transactional
    public void deleteVersion(Long versionId, Long userId) {
        NoteVersion nv = noteVersionRepository.findByIdAndUserId(versionId, userId)
                .orElseThrow(() -> new RuntimeException("版本不存在或无权访问"));
        noteVersionRepository.delete(nv);
        log.info("笔记版本已删除: versionId={}, snippetId={}, v{}", versionId, nv.getSnippetId(), nv.getVersionNumber());
    }

    private String buildSummary(String note) {
        if (note == null || note.isBlank()) return "(空笔记)";
        // 去除 HTML 标签
        String plain = note.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        if (plain.length() <= 100) return plain;
        return plain.substring(0, 100) + "...";
    }
}
