package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.dto.RepoImportRequest;
import com.coderag.entity.AsyncTask;
import com.coderag.entity.CodeRepository;
import com.coderag.repository.CodeChunkRepository;
import com.coderag.service.RepoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 仓库管理控制器
 */
@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;
    private final CodeChunkRepository codeChunkRepository;

    /**
     * 导入仓库
     */
    @PostMapping("/import")
    public R<AsyncTask> importRepo(@Valid @RequestBody RepoImportRequest request,
                                    Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(repoService.importRepo(userId, request.getRepoUrl(), request.getBranch()));
    }

    /**
     * 获取用户仓库列表
     */
    @GetMapping
    public R<Page<CodeRepository>> listRepos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(repoService.getUserRepos(userId, page, size));
    }

    /**
     * 获取仓库详情
     */
    @GetMapping("/{repoId}")
    public R<CodeRepository> getRepoDetail(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(repoService.getRepoDetail(repoId, userId));
    }

    /**
     * 删除仓库
     */
    @DeleteMapping("/{repoId}")
    public R<Void> deleteRepo(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        repoService.deleteRepo(repoId, userId);
        return R.ok();
    }

    /**
     * 重新解析仓库
     */
    @PostMapping("/{repoId}/reimport")
    public R<AsyncTask> reImportRepo(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(repoService.reImportRepo(repoId, userId));
    }

    /**
     * 查询任务进度
     */
    @GetMapping("/tasks/{taskId}")
    public R<AsyncTask> getTaskStatus(@PathVariable Long taskId) {
        return R.ok(repoService.getTaskStatus(taskId));
    }

    /**
     * 获取用户任务列表
     */
    @GetMapping("/tasks")
    public R<List<AsyncTask>> getUserTasks(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(repoService.getUserTasks(userId));
    }

    /**
     * 获取仓库文件列表（用于翻译等功能选择文件）
     */
    @GetMapping("/{repoId}/files")
    public R<List<String>> getRepoFiles(@PathVariable Long repoId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        repoService.getRepoDetail(repoId, userId); // 校验权限
        List<String> files = codeChunkRepository.findByRepoId(repoId).stream()
                .map(c -> c.getFilePath())
                .filter(f -> f != null && !f.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return R.ok(files);
    }
}
