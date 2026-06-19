package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.NoteVersion;
import com.coderag.service.NoteVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 笔记版本控制器
 */
@RestController
@RequestMapping("/api/note-versions")
@RequiredArgsConstructor
public class NoteVersionController {

    private final NoteVersionService noteVersionService;

    /** 保存笔记（创建新版本） */
    @PostMapping("/{snippetId}")
    public R<NoteVersion> saveVersion(@PathVariable Long snippetId,
                                      @RequestBody Map<String, String> body,
                                      Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String note = body.get("note");
        String tags = body.get("tags");
        String versionLabel = body.get("versionLabel");
        return R.ok(noteVersionService.saveVersion(snippetId, userId, note, tags, versionLabel));
    }

    /** 获取某片段的所有笔记版本 */
    @GetMapping("/{snippetId}")
    public R<List<NoteVersion>> getVersions(@PathVariable Long snippetId,
                                            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(noteVersionService.getVersions(snippetId, userId));
    }

    /** 获取某个版本详情 */
    @GetMapping("/detail/{versionId}")
    public R<NoteVersion> getVersion(@PathVariable Long versionId,
                                     Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(noteVersionService.getVersion(versionId, userId));
    }

    /** 回滚到指定版本 */
    @PostMapping("/rollback/{versionId}")
    public R<NoteVersion> rollback(@PathVariable Long versionId,
                                   Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(noteVersionService.rollbackToVersion(versionId, userId));
    }

    /** 删除某个版本 */
    @DeleteMapping("/{versionId}")
    public R<Void> deleteVersion(@PathVariable Long versionId,
                                 Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        noteVersionService.deleteVersion(versionId, userId);
        return R.ok();
    }
}
