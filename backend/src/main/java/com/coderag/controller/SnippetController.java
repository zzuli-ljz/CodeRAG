package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.CodeSnippet;
import com.coderag.service.SnippetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 代码片段收藏控制器
 */
@RestController
@RequestMapping("/api/snippets")
@RequiredArgsConstructor
public class SnippetController {

    private final SnippetService snippetService;

    /** 收藏代码片段 */
    @PostMapping
    public R<CodeSnippet> save(@RequestBody Map<String, Object> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long repoId = body.get("repoId") != null ? ((Number) body.get("repoId")).longValue() : null;
        String filePath = (String) body.get("filePath");
        String language = (String) body.get("language");
        String content = (String) body.get("content");
        String title = (String) body.get("title");
        String note = (String) body.get("note");
        String tags = (String) body.get("tags");
        Integer startLine = body.get("startLine") != null ? ((Number) body.get("startLine")).intValue() : null;
        Integer endLine = body.get("endLine") != null ? ((Number) body.get("endLine")).intValue() : null;

        if (content == null || content.isBlank()) {
            return R.fail("代码内容不能为空");
        }
        return R.ok(snippetService.save(userId, repoId, filePath, language, content, title, note, tags, startLine, endLine));
    }

    /** 获取单个片段 */
    @GetMapping("/{id}")
    public R<CodeSnippet> getById(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(snippetService.getById(id, userId));
    }

    /** 更新笔记 */
    @PutMapping("/{id}")
    public R<CodeSnippet> updateNote(@PathVariable Long id,
                                     @RequestBody Map<String, String> body,
                                     Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(snippetService.updateNote(id, userId, body.get("note"), body.get("tags")));
    }

    /** 删除收藏 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        snippetService.delete(id, userId);
        return R.ok();
    }

    /** 获取收藏列表 */
    @GetMapping
    public R<Page<CodeSnippet>> list(
            @RequestParam(required = false) Long repoId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(snippetService.list(userId, repoId, keyword, page, size));
    }

    /** 获取用户标签 */
    @GetMapping("/tags")
    public R<List<String>> getTags(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(snippetService.getUserTags(userId));
    }

    /** 导出为 Markdown */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportMarkdown(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String markdown = snippetService.exportMarkdown(userId);
        byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=code-snippets.md")
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(bytes);
    }

    /** 获取某仓库下已收藏的片段（用于恢复收藏按钮状态） */
    @GetMapping("/collected/{repoId}")
    public R<List<Map<String, Object>>> getCollectedByRepo(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(snippetService.getCollectedByRepo(userId, repoId));
    }
}
