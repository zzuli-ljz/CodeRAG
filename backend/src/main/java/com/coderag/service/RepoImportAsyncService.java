package com.coderag.service;

import com.coderag.VectorStore;
import com.coderag.adapter.RepoDataAdapter;
import com.coderag.adapter.model.RepoFile;
import com.coderag.adapter.model.RepoInfo;
import com.coderag.common.cache.CacheService;
import com.coderag.common.constant.TaskStatus;
import com.coderag.entity.AsyncTask;
import com.coderag.entity.CodeChunk;
import com.coderag.entity.CodeRepository;
import com.coderag.rag.CodeChunker;
import com.coderag.repository.AsyncTaskRepository;
import com.coderag.repository.CodeRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 仓库导入异步执行服务
 * 
 * 关键设计：@Async 方法必须放在独立的 Bean 中，Spring AOP 代理才能拦截。
 * 如果在 RepoService 内部直接调用 this.executeImportAsync()，会绕过代理，
 * 导致 @Async 不生效，整个导入管线在 HTTP 请求线程中同步执行，最终 Nginx 超时 502。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepoImportAsyncService {

    private final CodeRepositoryRepository codeRepositoryRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final QuotaService quotaService;
    private final CodeChunker codeChunker;
    private final VectorStore vectorStore;
    private final CacheService cacheService;

    /**
     * 异步执行仓库导入 - 完整管线
     * 
     * 注意：@Async 方法运行在独立线程中，不能依赖外层事务。
     * 如果查不到 task/repo（可能事务尚未提交），会重试最多 5 次，每次间隔 500ms。
     */
    @Async("taskExecutor")
    public void executeImportAsync(Long taskId, Long repoId, String repoUrl, String branch,
                                    RepoDataAdapter adapter, Long userId) {
        AsyncTask task = waitForEntity(() -> asyncTaskRepository.findById(taskId).orElse(null), "task", taskId);
        CodeRepository repo = waitForEntity(() -> codeRepositoryRepository.findById(repoId).orElse(null), "repo", repoId);

        if (task == null) {
            log.error("异步导入失败: taskId={} 在重试后仍不存在，放弃执行", taskId);
            return;
        }
        if (repo == null) {
            log.error("异步导入失败: repoId={} 在重试后仍不存在，放弃执行", repoId);
            task.setStatus(TaskStatus.FAILED);
            task.setStatusMessage("导入失败: 仓库记录异常");
            task.setErrorMessage("仓库记录在数据库中不存在");
            asyncTaskRepository.save(task);
            return;
        }

        try {
            executeImportPipeline(task, repoId, repoUrl, branch, adapter, userId);
        } catch (Exception e) {
            log.error("仓库导入失败: repoUrl={}", repoUrl, e);
            // 重新从数据库加载，避免 detached entity 问题
            AsyncTask freshTask = asyncTaskRepository.findById(taskId).orElse(task);
            freshTask.setStatus(TaskStatus.FAILED);
            freshTask.setErrorMessage(translateErrorMessage(e));
            freshTask.setStatusMessage("导入失败: " + translateErrorMessage(e));
            asyncTaskRepository.save(freshTask);

            CodeRepository freshRepo = codeRepositoryRepository.findById(repoId).orElse(repo);
            freshRepo.setStatus(TaskStatus.FAILED);
            codeRepositoryRepository.save(freshRepo);
        }
    }

    /**
     * 等待实体在数据库中出现（处理事务提交延迟）
     */
    private <T> T waitForEntity(java.util.function.Supplier<T> supplier, String entityName, Long id) {
        for (int i = 0; i < 5; i++) {
            T entity = supplier.get();
            if (entity != null) return entity;
            log.warn("等待 {} id={} 出现... (第{}次重试)", entityName, id, i + 1);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    /**
     * 异步执行重新导入（清除旧数据 → 完整导入管线）
     */
    @Async("taskExecutor")
    public void executeReImportAsync(Long taskId, Long repoId, String repoUrl, String branch,
                                      RepoDataAdapter adapter, Long userId) {
        AsyncTask task = waitForEntity(() -> asyncTaskRepository.findById(taskId).orElse(null), "task", taskId);
        if (task == null) {
            log.error("异步重新导入失败: taskId={} 在重试后仍不存在", taskId);
            return;
        }

        try {
            // 阶段0：清除旧向量数据
            task.setStatus(TaskStatus.RUNNING);
            task.setProgress(5);
            task.setStatusMessage("正在清除旧的代码数据...");
            asyncTaskRepository.save(task);
            vectorStore.deleteByRepoId(repoId);

            // 接下来走正常导入管线
            executeImportPipeline(task, repoId, repoUrl, branch, adapter, userId);
        } catch (Exception e) {
            log.error("重新导入失败: repoId={}, error={}", repoId, e.getMessage(), e);
            AsyncTask freshTask = asyncTaskRepository.findById(taskId).orElse(task);
            freshTask.setStatus(TaskStatus.FAILED);
            freshTask.setStatusMessage("重新导入失败: " + translateErrorMessage(e));
            freshTask.setErrorMessage(translateErrorMessage(e));
            asyncTaskRepository.save(freshTask);

            CodeRepository repo = codeRepositoryRepository.findById(repoId).orElse(null);
            if (repo != null) {
                repo.setStatus(TaskStatus.FAILED);
                codeRepositoryRepository.save(repo);
            }
        }
    }

    /**
     * 共享的导入管线逻辑（获取仓库信息 → 获取文件 → 分块 → 向量化 → 入库）
     */
    private void executeImportPipeline(AsyncTask task, Long repoId, String repoUrl, String branch,
                                        RepoDataAdapter adapter, Long userId) {
        CodeRepository repo = codeRepositoryRepository.findById(repoId).orElseThrow();

        // 阶段0：获取仓库元信息（网络操作，移到异步中避免阻塞 HTTP 响应）
        task.setStatus(TaskStatus.RUNNING);
        task.setProgress(5);
        task.setStatusMessage("正在获取仓库信息...");
        asyncTaskRepository.save(task);

        try {
            RepoInfo repoInfo = adapter.fetchRepoInfo(repoUrl);
            repo.setRepoName(repoInfo.getName());
            repo.setRepoOwner(repoInfo.getOwner());
            repo.setDescription(repoInfo.getDescription());
            repo.setLanguage(repoInfo.getLanguage());
            if (branch == null || branch.isEmpty()) {
                branch = repoInfo.getDefaultBranch();
                repo.setDefaultBranch(branch);
            }
            codeRepositoryRepository.save(repo);
        } catch (Exception e) {
            log.error("获取仓库信息失败: repoUrl={}", repoUrl, e);
            throw new RuntimeException("无法获取仓库信息，请检查链接是否正确或仓库是否公开: " + e.getMessage());
        }

        // 阶段1：获取文件列表
        task.setProgress(10);
        task.setStatusMessage("正在获取文件列表...");
        asyncTaskRepository.save(task);

        List<RepoFile> codeFiles = adapter.fetchAllCodeFiles(repoUrl, branch);

        task.setProgress(30);
        task.setStatusMessage("获取到 " + codeFiles.size() + " 个代码文件，开始解析...");
        asyncTaskRepository.save(task);

        repo.setFileCount(codeFiles.size());
        codeRepositoryRepository.save(repo);

        if (codeFiles.isEmpty()) {
            task.setProgress(100);
            task.setStatus(TaskStatus.COMPLETED);
            task.setStatusMessage("仓库中无代码文件");
            asyncTaskRepository.save(task);

            repo.setStatus(TaskStatus.COMPLETED);
            codeRepositoryRepository.save(repo);
            return;
        }

        // 阶段2：代码分块
        task.setProgress(40);
        task.setStatusMessage("正在分块处理代码...");
        asyncTaskRepository.save(task);

        List<CodeChunk> allChunks = new ArrayList<>();
        long totalLines = 0;
        for (RepoFile file : codeFiles) {
            if (file.getContent() == null || file.getContent().isEmpty()) continue;
            List<CodeChunk> chunks = codeChunker.chunkFile(repoId, file.getPath(), file.getLanguage(), file.getContent());
            allChunks.addAll(chunks);
            totalLines += file.getContent().split("\n").length;
        }

        repo.setCodeLineCount(totalLines);
        codeRepositoryRepository.save(repo);

        task.setProgress(50);
        task.setStatusMessage("代码分块完成，共 " + allChunks.size() + " 个分块，开始向量化...");
        asyncTaskRepository.save(task);

        // 阶段3：向量化 + 入库
        int chunkCount = allChunks.size();
        int savedCount = 0;
        int batchSize = 20;

        for (int i = 0; i < chunkCount; i += batchSize) {
            int end = Math.min(i + batchSize, chunkCount);
            List<CodeChunk> batch = allChunks.subList(i, end);

            savedCount += vectorStore.saveChunksWithAutoEmbedding(batch);

            int progress = 50 + (int) ((double) savedCount / chunkCount * 45);
            task.setProgress(progress);
            task.setStatusMessage("已向量化 " + savedCount + "/" + chunkCount + " 个分块...");
            asyncTaskRepository.save(task);
        }

        // 阶段4：完成
        long storageBytes = allChunks.stream()
                .mapToLong(c -> c.getContent() != null ? c.getContent().length() : 0)
                .sum();
        repo.setStorageBytes(storageBytes);
        repo.setStatus(TaskStatus.COMPLETED);
        codeRepositoryRepository.save(repo);

        task.setProgress(100);
        task.setStatus(TaskStatus.COMPLETED);
        task.setStatusMessage("导入完成，共 " + codeFiles.size() + " 个文件，" + allChunks.size() + " 个分块");
        asyncTaskRepository.save(task);

        log.info("仓库导入完成: repoId={}, files={}, chunks={}, lines={}",
                repoId, codeFiles.size(), allChunks.size(), totalLines);

        // 配额计数已在 RepoService.importRepo() 入口处预扣，此处不再重复计数
    }

    /**
     * 将技术异常翻译为用户可读的错误消息
     */
    private String translateErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "未知错误，请稍后重试";

        // GitHub/Gitee API 限流
        if (msg.contains("429") || msg.contains("403") || msg.contains("限流")) {
            return "API 调用次数已达上限，请稍后重试";
        }
        // 网络连接失败
        if (msg.contains("connect") || msg.contains("timeout") || msg.contains("SocketTimeout") || msg.contains("Connection refused")) {
            return "网络连接失败，无法访问仓库，请检查仓库链接是否正确";
        }
        // 仓库不存在或私有
        if (msg.contains("404") || msg.contains("Not Found")) {
            return "仓库不存在或为私有仓库，请检查链接和权限";
        }
        // URL 格式错误
        if (msg.contains("格式不正确")) {
            return "仓库链接格式不正确，请使用 https://github.com/owner/repo 或 https://gitee.com/owner/repo 格式";
        }
        // 不支持的仓库
        if (msg.contains("不支持")) {
            return msg;
        }
        // 向量化失败
        if (msg.contains("embed") || msg.contains("vector") || msg.contains("向量化")) {
            return "代码向量化处理失败，请稍后重试";
        }
        // 其他：返回原始消息但限制长度
        if (msg.length() > 200) {
            return msg.substring(0, 200) + "...";
        }
        return msg;
    }

    /**
     * 定时清理：将超过 10 分钟仍处于 PENDING 状态的任务标记为 FAILED
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupStalePendingTasks() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
            List<AsyncTask> staleTasks = asyncTaskRepository
                    .findByStatusAndCreatedAtBefore(TaskStatus.PENDING, threshold);
            for (AsyncTask task : staleTasks) {
                log.warn("清理卡住的 PENDING 任务: taskId={}, createdAt={}", task.getId(), task.getCreatedAt());
                task.setStatus(TaskStatus.FAILED);
                task.setStatusMessage("任务超时未启动，请重试");
                task.setErrorMessage("任务创建超过10分钟仍未开始执行，可能由于系统繁忙");
                asyncTaskRepository.save(task);

                if (task.getRepoId() != null) {
                    codeRepositoryRepository.findById(task.getRepoId()).ifPresent(repo -> {
                        repo.setStatus(TaskStatus.FAILED);
                        codeRepositoryRepository.save(repo);
                    });
                }
            }
            if (!staleTasks.isEmpty()) {
                log.info("已清理 {} 个卡住的 PENDING 任务", staleTasks.size());
            }
        } catch (Exception e) {
            log.error("清理卡住任务时出错", e);
        }
    }
}
