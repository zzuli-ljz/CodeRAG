package com.coderag.service;

import com.coderag.adapter.RepoDataAdapter;
import com.coderag.common.cache.CacheService;
import com.coderag.common.constant.PlatformConstant;
import com.coderag.common.constant.TaskStatus;
import com.coderag.entity.AsyncTask;
import com.coderag.entity.CodeRepository;
import com.coderag.exception.BusinessException;
import com.coderag.VectorStore;
import com.coderag.repository.AsyncTaskRepository;
import com.coderag.repository.CodeRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 仓库服务 - 同步入口层
 * 
 * 所有耗时操作（网络请求、文件拉取、分块、向量化）均委托给
 * RepoImportAsyncService 在后台线程池中异步执行。
 * 本层只做：参数校验 → 创建记录 → 触发异步任务 → 立即返回任务ID。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepoService {

    private final CodeRepositoryRepository codeRepositoryRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final QuotaService quotaService;
    private final VectorStore vectorStore;
    private final CacheService cacheService;
    private final List<RepoDataAdapter> adapters;
    private final RepoImportAsyncService repoImportAsyncService;

    /**
     * 导入仓库 - 快速返回任务ID，所有耗时操作均异步执行
     */
    @org.springframework.transaction.annotation.Transactional
    public AsyncTask importRepo(Long userId, String repoUrl, String branch) {
        quotaService.checkImportQuota(userId);
        // 检查通过后立即预扣配额计数，防止异步任务并发导致超限
        quotaService.incrementImportCount(userId);

        // 识别平台（纯本地操作，不涉及网络）
        RepoDataAdapter adapter = adapters.stream()
                .filter(a -> a.supports(repoUrl))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的仓库链接，仅支持 GitHub / Gitee"));

        // 解析 URL 获取 owner/repo（纯本地操作）
        Map<String, String> parsed = adapter.parseRepoUrl(repoUrl);
        String resolvedBranch = (branch != null && !branch.isEmpty()) ? branch : parsed.getOrDefault("branch", "");

        // 先创建仓库记录（基本信息，后续异步补齐）
        CodeRepository repo = new CodeRepository();
        repo.setUserId(userId);
        repo.setPlatform(repoUrl.contains("github") ? PlatformConstant.GITHUB : PlatformConstant.GITEE);
        repo.setRepoUrl(repoUrl);
        repo.setRepoName(parsed.getOrDefault("repo", ""));
        repo.setRepoOwner(parsed.getOrDefault("owner", ""));
        repo.setDefaultBranch(resolvedBranch);
        repo.setStatus(TaskStatus.PENDING);
        repo = codeRepositoryRepository.save(repo);

        // 创建异步任务
        AsyncTask task = new AsyncTask();
        task.setUserId(userId);
        task.setRepoId(repo.getId());
        task.setTaskType("REPO_IMPORT");
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setStatusMessage("正在获取仓库信息...");
        task = asyncTaskRepository.save(task);

        // 委托给独立的异步服务（Spring AOP 代理能正确拦截 @Async）
        repoImportAsyncService.executeImportAsync(task.getId(), repo.getId(), repoUrl, resolvedBranch, adapter, userId);

        return task;
    }

    /**
     * 获取用户的仓库列表
     */
    public Page<CodeRepository> getUserRepos(Long userId, int page, int size) {
        return codeRepositoryRepository.findByUserId(userId, PageRequest.of(page, size));
    }

    /**
     * 获取仓库详情
     */
    public CodeRepository getRepoDetail(Long repoId, Long userId) {
        CodeRepository repo = codeRepositoryRepository.findById(repoId)
                .orElseThrow(() -> new BusinessException("仓库不存在"));
        if (!repo.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该仓库");
        }
        return repo;
    }

    /**
     * 删除仓库
     */
    public void deleteRepo(Long repoId, Long userId) {
        CodeRepository repo = getRepoDetail(repoId, userId);
        vectorStore.deleteByRepoId(repoId);
        codeRepositoryRepository.delete(repo);
    }

    /**
     * 查询异步任务状态
     */
    public AsyncTask getTaskStatus(Long taskId) {
        return asyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("任务不存在"));
    }

    /**
     * 获取用户的所有任务
     */
    public List<AsyncTask> getUserTasks(Long userId) {
        return asyncTaskRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 重新解析仓库（快速返回，所有重活放后台线程）
     */
    public AsyncTask reImportRepo(Long repoId, Long userId) {
        CodeRepository repo = getRepoDetail(repoId, userId);

        // 清除缓存，强制重新拉取
        RepoDataAdapter adapter = adapters.stream()
                .filter(a -> a.supports(repo.getRepoUrl()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("无法识别该仓库的平台类型"));

        Map<String, String> parsed = adapter.parseRepoUrl(repo.getRepoUrl());
        String cacheKey = CacheService.repoKey(
                repo.getPlatform().toLowerCase(),
                parsed.get("owner"), parsed.get("repo"), repo.getDefaultBranch());
        cacheService.remove(cacheKey);

        // 创建新的异步任务
        AsyncTask task = new AsyncTask();
        task.setUserId(userId);
        task.setRepoId(repoId);
        task.setTaskType("REPO_IMPORT");
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setStatusMessage("等待重新解析");
        task = asyncTaskRepository.save(task);

        repo.setStatus(TaskStatus.PENDING);
        codeRepositoryRepository.save(repo);

        // 委托给独立的异步服务
        repoImportAsyncService.executeReImportAsync(task.getId(), repoId, repo.getRepoUrl(),
                repo.getDefaultBranch(), adapter, userId);

        return task;
    }
}
