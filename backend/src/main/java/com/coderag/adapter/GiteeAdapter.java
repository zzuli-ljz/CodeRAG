package com.coderag.adapter;

import com.coderag.adapter.model.RepoFile;
import com.coderag.adapter.model.RepoInfo;
import com.coderag.common.cache.CacheService;
import com.coderag.rag.CodeFileFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Gitee V5 开放 API 适配器
 * - Authorization: token {token} 固定携带
 * - 递归遍历仓库目录，读取文件内容
 * - 文件内容 base64 自动解码
 * - 过滤非代码文件
 * - 缓存机制：同一仓库导入完成后缓存文件内容
 * - 限流：批量拉取文件时间隔 200ms，防止触发 429
 */
@Slf4j
@Component
public class GiteeAdapter implements RepoDataAdapter {

    @Value("${gitee.api.token:}")
    private String apiToken;

    @Value("${gitee.api.base-url:https://gitee.com/api/v5}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final CacheService cacheService;

    /** 批量拉取文件时间隔（ms），防止触发 429 限流 */
    private static final long REQUEST_INTERVAL_MS = 200;

    public GiteeAdapter(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public boolean supports(String repoUrl) {
        return repoUrl != null && repoUrl.contains("gitee.com");
    }

    @Override
    public RepoInfo fetchRepoInfo(String repoUrl) {
        Map<String, String> parsed = parseRepoUrl(repoUrl);
        String owner = parsed.get("owner");
        String repo = parsed.get("repo");
        String url = baseUrl + "/repos/" + owner + "/" + repo;

        JsonNode node = httpGet(url);
        RepoInfo info = new RepoInfo();
        info.setName(node.path("name").asText());
        info.setOwner(node.path("owner").path("login").asText());
        info.setDescription(node.path("description").asText(""));
        info.setDefaultBranch(node.path("default_branch").asText("master"));
        info.setLanguage(node.path("language").asText(""));
        info.setSize(node.path("size").asLong(0));
        info.setHtmlUrl(node.path("html_url").asText());
        return info;
    }

    @Override
    public List<RepoFile> fetchFileTree(String repoUrl, String branch) {
        Map<String, String> parsed = parseRepoUrl(repoUrl);
        String owner = parsed.get("owner");
        String repo = parsed.get("repo");
        if (branch == null || branch.isEmpty()) {
            branch = parsed.getOrDefault("branch", "master");
        }

        // Gitee V5 git trees 接口：使用分支名或 SHA
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1";

        JsonNode node = httpGet(url);
        List<RepoFile> files = new ArrayList<>();
        JsonNode tree = node.path("tree");

        for (JsonNode item : tree) {
            RepoFile file = new RepoFile();
            file.setPath(item.path("path").asText());
            file.setName(extractFileName(file.getPath()));
            file.setDirectory("tree".equals(item.path("type").asText()));
            file.setSize(item.path("size").asLong(0));
            files.add(file);
        }
        return files;
    }

    @Override
    public String fetchFileContent(String repoUrl, String branch, String filePath) {
        Map<String, String> parsed = parseRepoUrl(repoUrl);
        String owner = parsed.get("owner");
        String repo = parsed.get("repo");
        if (branch == null || branch.isEmpty()) {
            branch = parsed.getOrDefault("branch", "master");
        }
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/contents/" + filePath + "?ref=" + branch;

        JsonNode node = httpGet(url);
        String encoding = node.path("encoding").asText();
        String content = node.path("content").asText();

        if ("base64".equals(encoding)) {
            return new String(Base64.getDecoder().decode(content.replace("\n", "")));
        }
        return content;
    }

    @Override
    public List<RepoFile> fetchAllCodeFiles(String repoUrl, String branch) {
        Map<String, String> parsed = parseRepoUrl(repoUrl);
        String owner = parsed.get("owner");
        String repo = parsed.get("repo");
        if (branch == null || branch.isEmpty()) {
            branch = parsed.getOrDefault("branch", "master");
        }

        // 1. 检查缓存
        String cacheKey = CacheService.repoKey("gitee", owner, repo, branch);
        Optional<List<RepoFile>> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            log.info("Gitee 仓库 {}/{} 分支 {} 命中缓存", owner, repo, branch);
            return cached.get();
        }

        // 2. 获取文件树
        List<RepoFile> allFiles = fetchFileTree(repoUrl, branch);

        // 3. 过滤非代码文件
        List<RepoFile> codeFiles = allFiles.stream()
                .filter(f -> !f.isDirectory())
                .filter(f -> CodeFileFilter.isCodeFile(f.getPath()))
                .toList();

        log.info("Gitee 仓库 {}/{} 共 {} 个文件，过滤后代码文件 {} 个",
                owner, repo, allFiles.size(), codeFiles.size());

        // 4. 逐个获取文件内容（限流 200ms 间隔）
        List<RepoFile> result = new ArrayList<>();
        for (int i = 0; i < codeFiles.size(); i++) {
            RepoFile file = codeFiles.get(i);
            try {
                // 限流：每次请求间隔 200ms
                if (i > 0) {
                    Thread.sleep(REQUEST_INTERVAL_MS);
                }

                String content = fetchFileContent(repoUrl, branch, file.getPath());
                file.setContent(content);
                file.setLanguage(file.inferLanguage());
                result.add(file);

                if ((i + 1) % 20 == 0) {
                    log.info("Gitee 仓库 {}/{} 已拉取 {}/{} 个代码文件",
                            owner, repo, i + 1, codeFiles.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Gitee 文件拉取被中断: {}/{}", owner, repo);
                break;
            } catch (Exception e) {
                log.warn("Gitee 文件 {} 内容获取失败，跳过: {}", file.getPath(), e.getMessage());
            }
        }

        // 5. 写入缓存
        cacheService.putRepoCache(cacheKey, result);
        log.info("Gitee 仓库 {}/{} 分支 {} 导入完成，缓存 {} 个代码文件",
                owner, repo, branch, result.size());

        return result;
    }

    /** 单个文件 diff 最大行数（超过则截断） */
    private static final int MAX_DIFF_LINES_PER_FILE = 500;
    /** diff 总最大字符数（防止 AI token 爆炸） */
    private static final int MAX_DIFF_TOTAL_CHARS = 80_000;

    @Override
    public String fetchDiff(String repoUrl, String sourceRef, String targetRef) {
        Map<String, String> parsed = parseRepoUrl(repoUrl);
        String owner = parsed.get("owner");
        String repo = parsed.get("repo");

        // Gitee V5 对比 API
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/compare/" + sourceRef + "..." + targetRef;

        try {
            JsonNode node = httpGet(url);
            StringBuilder sb = new StringBuilder();

            // 汇总信息
            int aheadBy = node.path("ahead_by").asInt(0);
            int behindBy = node.path("behind_by").asInt(0);
            int totalCommits = node.path("total_commits").asInt(0);
            sb.append("## 对比概要\n");
            sb.append("- 领先提交: ").append(aheadBy).append(" 个\n");
            sb.append("- 落后提交: ").append(behindBy).append(" 个\n");
            sb.append("- 总提交数: ").append(totalCommits).append(" 个\n\n");

            // 提交信息
            JsonNode commits = node.path("commits");
            if (commits.isArray() && commits.size() > 0) {
                sb.append("## 提交记录\n");
                for (JsonNode commit : commits) {
                    String sha = commit.path("sha").asText().substring(0, Math.min(7, commit.path("sha").asText().length()));
                    String msg = commit.path("commit").path("message").asText().split("\\n")[0];
                    sb.append("- `").append(sha).append("` ").append(msg).append("\n");
                }
                sb.append("\n");
            }

            // 文件变更（带过滤和截断）
            JsonNode files = node.path("files");
            if (files.isArray() && files.size() > 0) {
                sb.append("## 文件变更详情\n\n");
                int skippedFiles = 0;
                int truncatedFiles = 0;
                int totalSize = 0;

                for (JsonNode file : files) {
                    String filename = file.path("filename").asText();
                    int additions = file.path("additions").asInt(0);
                    int deletions = file.path("deletions").asInt(0);
                    int changes = additions + deletions;

                    // 1. 跳过非代码文件
                    if (isNonCodeFile(filename)) {
                        skippedFiles++;
                        continue;
                    }

                    // 2. 跳过超大变更
                    if (changes > 2000) {
                        skippedFiles++;
                        continue;
                    }

                    String patch = file.path("patch").asText("");
                    if (patch.isEmpty()) {
                        skippedFiles++;
                        continue;
                    }

                    // 3. 截断过长 diff
                    String[] lines = patch.split("\n");
                    if (lines.length > MAX_DIFF_LINES_PER_FILE) {
                        StringBuilder truncated = new StringBuilder();
                        for (int i = 0; i < MAX_DIFF_LINES_PER_FILE; i++) {
                            truncated.append(lines[i]).append("\n");
                        }
                        truncated.append("... (截断，共 ").append(lines.length)
                                .append(" 行，仅显示前 ").append(MAX_DIFF_LINES_PER_FILE).append(" 行)\n");
                        patch = truncated.toString();
                        truncatedFiles++;
                    }

                    // 4. 检查总大小限制
                    String entry = "### " + filename + " (+" + additions + " -" + deletions + ")\n\n```diff\n" + patch + "\n```\n\n";
                    if (totalSize + entry.length() > MAX_DIFF_TOTAL_CHARS) {
                        skippedFiles++;
                        continue;
                    }

                    sb.append(entry);
                    totalSize += entry.length();
                }

                if (skippedFiles > 0) {
                    sb.append("> 已过滤 ").append(skippedFiles).append(" 个非代码/超大文件");
                    if (truncatedFiles > 0) {
                        sb.append("，截断 ").append(truncatedFiles).append(" 个过长文件");
                    }
                    sb.append("\n\n");
                }
            }

            if (sb.length() == 0) {
                return "两个版本之间没有差异。";
            }
            return sb.toString();
        } catch (Exception e) {
            // Gitee 对比 API 可能不支持跨 fork 对比，降级处理
            log.warn("Gitee Compare API 失败，尝试降级方案: {}", e.getMessage());
            String errMsg = e.getMessage() != null ? e.getMessage() : "未知错误";

            // 降级方案1：尝试通过 PR API 查找相关 PR 获取 diff（跨 fork 场景）
            try {
                String prDiff = tryFetchPrDiff(owner, repo, sourceRef, targetRef);
                if (prDiff != null && !prDiff.isEmpty()) {
                    return prDiff;
                }
            } catch (Exception prEx) {
                log.warn("PR diff 获取也失败: {}", prEx.getMessage());
            }

            // 降级方案2：尝试获取两个 ref 各自的提交信息做对比
            try {
                StringBuilder fallback = new StringBuilder();
                fallback.append("## 版本对比（基于提交信息）\n\n");
                fallback.append("> 注意：Gitee Compare API 调用失败，以下为两个版本的提交信息摘要。\n");
                fallback.append("> 原因：").append(errMsg).append("\n\n");

                // 尝试通过 PR 查找源分支所在的 fork 仓库
                String forkOwner = tryFindForkOwner(owner, repo, sourceRef);

                // 获取源 ref 的提交（优先从 fork 仓库获取）
                String srcOwner = forkOwner != null ? forkOwner : owner;
                String srcUrl = baseUrl + "/repos/" + srcOwner + "/" + repo + "/commits?sha=" + sourceRef + "&per_page=5";
                try {
                    JsonNode srcCommits = httpGet(srcUrl);
                    fallback.append("### 源分支 `").append(sourceRef).append("` 最近提交");
                    if (forkOwner != null) {
                        fallback.append("（来自 ").append(forkOwner).append(" 的 fork）");
                    }
                    fallback.append("\n");
                    for (JsonNode c : srcCommits) {
                        String sha = c.path("sha").asText().substring(0, Math.min(7, c.path("sha").asText().length()));
                        String msg = c.path("commit").path("message").asText().split("\\n")[0];
                        fallback.append("- `").append(sha).append("` ").append(msg).append("\n");
                    }
                } catch (Exception ignored) {
                    fallback.append("- 无法获取源分支提交信息\n");
                }

                fallback.append("\n### 目标分支 `").append(targetRef).append("` 最近提交\n");
                String tgtUrl = baseUrl + "/repos/" + owner + "/" + repo + "/commits?sha=" + targetRef + "&per_page=5";
                try {
                    JsonNode tgtCommits = httpGet(tgtUrl);
                    for (JsonNode c : tgtCommits) {
                        String sha = c.path("sha").asText().substring(0, Math.min(7, c.path("sha").asText().length()));
                        String msg = c.path("commit").path("message").asText().split("\\n")[0];
                        fallback.append("- `").append(sha).append("` ").append(msg).append("\n");
                    }
                } catch (Exception ignored) {
                    fallback.append("- 无法获取目标分支提交信息\n");
                }

                fallback.append("\n建议在 Gitee 页面查看完整对比: ").append(repoUrl).append("/compare/").append(sourceRef).append("...").append(targetRef);
                return fallback.toString();
            } catch (Exception fallbackErr) {
                log.warn("Gitee 降级方案也失败: {}", fallbackErr.getMessage());
                return "Gitee diff 获取失败: " + errMsg + "\n\n建议在 Gitee 页面查看对比: " + repoUrl + "/compare/" + sourceRef + "..." + targetRef;
            }
        }
    }

    /**
     * 降级方案1：尝试通过 PR API 查找相关 PR 并获取 diff
     * 适用于跨 fork 对比场景
     */
    private String tryFetchPrDiff(String owner, String repo, String sourceRef, String targetRef) {
        // 查找源分支对应目标分支的 PR（状态为 open）
        String prListUrl = baseUrl + "/repos/" + owner + "/" + repo + "/pulls?state=open&head=" + sourceRef + "&base=" + targetRef;
        try {
            JsonNode prs = httpGet(prListUrl);
            if (prs.isArray() && prs.size() > 0) {
                // 找到匹配的 PR
                for (JsonNode pr : prs) {
                    String headLabel = pr.path("head").path("label").asText("");
                    String baseLabel = pr.path("base").path("label").asText("");
                    if (headLabel.contains(sourceRef) && baseLabel.contains(targetRef)) {
                        int prNumber = pr.path("number").asInt();
                        log.info("找到匹配的 PR: #{}", prNumber);

                        // 获取 PR 的 diff
                        String prDiffUrl = baseUrl + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + ".diff";
                        try {
                            String diffContent = httpGetRaw(prDiffUrl);
                            if (diffContent != null && !diffContent.isEmpty()) {
                                StringBuilder sb = new StringBuilder();
                                sb.append("## PR #").append(prNumber).append(": ").append(pr.path("title").asText("")).append("\n\n");
                                sb.append("> 源分支: ").append(headLabel).append("\n");
                                sb.append("> 目标分支: ").append(baseLabel).append("\n");
                                sb.append("> 状态: ").append(pr.path("state").asText()).append("\n\n");
                                sb.append("### 文件变更\n\n");
                                sb.append("```diff\n").append(diffContent).append("\n```\n");
                                return sb.toString();
                            }
                        } catch (Exception e) {
                            log.warn("获取 PR #{} diff 失败: {}", prNumber, e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查找 PR 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 尝试查找源分支所在的 fork 仓库 owner
     * 通过 PR API 查找 head 信息
     */
    private String tryFindForkOwner(String owner, String repo, String sourceRef) {
        String prListUrl = baseUrl + "/repos/" + owner + "/" + repo + "/pulls?state=all&head=" + sourceRef + "&per_page=1";
        try {
            JsonNode prs = httpGet(prListUrl);
            if (prs.isArray() && prs.size() > 0) {
                JsonNode head = prs.get(0).path("head");
                String headUser = head.path("user").path("login").asText("");
                if (!headUser.isEmpty() && !headUser.equals(owner)) {
                    log.info("找到 fork 仓库 owner: {}", headUser);
                    return headUser;
                }
            }
        } catch (Exception e) {
            log.warn("查找 fork owner 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * HTTP GET 返回原始文本（用于获取 diff 内容）
     */
    private String httpGetRaw(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + apiToken)
                .header("Accept", "application/vnd.github.v3.diff")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            log.warn("httpGetRaw 请求失败: {} -> {}", url, response.code());
        } catch (Exception e) {
            log.warn("httpGetRaw 异常: {} -> {}", url, e.getMessage());
        }
        return null;
    }

    @Override
    public List<String> fetchBranches(String repoUrl) {
        Map<String, String> parsed = parseRepoUrl(repoUrl);
        String owner = parsed.get("owner");
        String repo = parsed.get("repo");
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/branches?per_page=100";

        JsonNode node = httpGet(url);
        List<String> branches = new ArrayList<>();
        for (JsonNode item : node) {
            branches.add(item.path("name").asText());
        }
        return branches;
    }

    @Override
    public Map<String, String> parseRepoUrl(String repoUrl) {
        Map<String, String> result = new HashMap<>();
        // 支持: https://gitee.com/owner/repo, https://gitee.com/owner/repo/tree/branch
        // 支持: https://gitee.com/owner/repo.git
        String trimmed = repoUrl
                .replaceAll("https?://gitee\\.com/", "")
                .replaceAll("\\.git$", "")
                .replaceAll("/$", "");

        String[] parts = trimmed.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Gitee 仓库链接格式不正确: " + repoUrl);
        }
        result.put("owner", parts[0]);
        result.put("repo", parts[1]);
        result.put("branch", parts.length >= 4 && "tree".equals(parts[2]) ? parts[3] : "");
        return result;
    }

    /**
     * Gitee V5 API GET 请求
     * 固定携带 Authorization: token {token}
     */
    private JsonNode httpGet(String url) {
        Request.Builder builder = new Request.Builder().url(url).get();
        builder.header("Content-Type", "application/json");
        if (apiToken != null && !apiToken.isEmpty()) {
            builder.header("Authorization", "token " + apiToken);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.code() == 403) {
                throw new RuntimeException("Gitee API 限流 (429/403)，请稍后重试");
            }
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("Gitee API 请求失败: " + response.code() + " - " + errBody);
            }
            String body = response.body().string();
            return objectMapper.readTree(body);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Gitee API 请求异常: " + e.getMessage(), e);
        }
    }

    private String extractFileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /**
     * 判断是否为非代码文件（lock 文件、minified、generated、二进制等）
     */
    private boolean isNonCodeFile(String filename) {
        String lower = filename.toLowerCase();
        // lock 文件
        if (lower.endsWith("package-lock.json") || lower.endsWith("yarn.lock")
                || lower.endsWith("pnpm-lock.yaml") || lower.endsWith("composer.lock")
                || lower.endsWith("gemfile.lock") || lower.endsWith("cargo.lock")
                || lower.endsWith("poetry.lock") || lower.endsWith("mix.lock")) {
            return true;
        }
        // minified / bundled
        if (lower.contains(".min.") || lower.endsWith(".min.js") || lower.endsWith(".min.css")
                || lower.endsWith(".bundle.js") || lower.endsWith(".bundle.css")) {
            return true;
        }
        // generated / vendor
        if (lower.contains("/generated/") || lower.contains("/vendor/")
                || lower.contains("/node_modules/") || lower.contains("/dist/")
                || lower.contains("/build/") || lower.contains("/.next/")
                || lower.contains("/target/") || lower.contains("/__pycache__/")) {
            return true;
        }
        // 二进制/资源文件
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".ico") || lower.endsWith(".svg")
                || lower.endsWith(".woff") || lower.endsWith(".woff2") || lower.endsWith(".ttf")
                || lower.endsWith(".eot") || lower.endsWith(".mp3") || lower.endsWith(".mp4")
                || lower.endsWith(".webm") || lower.endsWith(".ogg") || lower.endsWith(".pdf")
                || lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz")
                || lower.endsWith(".7z") || lower.endsWith(".rar") || lower.endsWith(".jar")
                || lower.endsWith(".war") || lower.endsWith(".ear") || lower.endsWith(".class")
                || lower.endsWith(".exe") || lower.endsWith(".dll") || lower.endsWith(".so")
                || lower.endsWith(".dylib") || lower.endsWith(".wasm") || lower.endsWith(".map")) {
            return true;
        }
        return false;
    }
}
