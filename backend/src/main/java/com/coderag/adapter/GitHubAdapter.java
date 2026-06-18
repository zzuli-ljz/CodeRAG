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
 * GitHub REST API v3 适配器
 * - Authorization: token {token} 固定携带
 * - 递归遍历仓库目录，读取文件内容
 * - 文件内容 base64 自动解码
 * - 过滤非代码文件
 * - 缓存机制：同一仓库导入完成后缓存文件内容
 * - 限流：批量拉取文件时间隔 200ms，防止 429
 */
@Slf4j
@Component
public class GitHubAdapter implements RepoDataAdapter {

    @Value("${github.api.token:}")
    private String apiToken;

    @Value("${github.api.base-url:https://api.github.com}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final CacheService cacheService;

    /** 批量拉取文件时间隔（ms），防止触发 429 限流 */
    private static final long REQUEST_INTERVAL_MS = 200;

    public GitHubAdapter(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public boolean supports(String repoUrl) {
        return repoUrl != null && (repoUrl.contains("github.com") || repoUrl.contains("githubusercontent.com"));
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
        info.setDefaultBranch(node.path("default_branch").asText("main"));
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
            branch = parsed.getOrDefault("branch", "main");
        }
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

        // 检查是否被截断（超大仓库）
        if (node.path("truncated").asBoolean(false)) {
            log.warn("GitHub 仓库 {} 文件树被截断，部分文件未获取", owner + "/" + repo);
        }

        return files;
    }

    @Override
    public String fetchFileContent(String repoUrl, String branch, String filePath) {
        Map<String, String> parsed = parseRepoUrl(repoUrl);
        String owner = parsed.get("owner");
        String repo = parsed.get("repo");
        if (branch == null || branch.isEmpty()) {
            branch = parsed.getOrDefault("branch", "main");
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
            branch = parsed.getOrDefault("branch", "main");
        }

        // 1. 检查缓存
        String cacheKey = CacheService.repoKey("github", owner, repo, branch);
        Optional<List<RepoFile>> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            log.info("GitHub 仓库 {}/{} 分支 {} 命中缓存", owner, repo, branch);
            return cached.get();
        }

        // 2. 获取文件树
        List<RepoFile> allFiles = fetchFileTree(repoUrl, branch);

        // 3. 过滤非代码文件
        List<RepoFile> codeFiles = allFiles.stream()
                .filter(f -> !f.isDirectory())
                .filter(f -> CodeFileFilter.isCodeFile(f.getPath()))
                .toList();

        log.info("GitHub 仓库 {}/{} 共 {} 个文件，过滤后代码文件 {} 个",
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
                    log.info("GitHub 仓库 {}/{} 已拉取 {}/{} 个代码文件",
                            owner, repo, i + 1, codeFiles.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("GitHub 文件拉取被中断: {}/{}", owner, repo);
                break;
            } catch (Exception e) {
                log.warn("GitHub 文件 {} 内容获取失败，跳过: {}", file.getPath(), e.getMessage());
            }
        }

        // 5. 写入缓存
        cacheService.putRepoCache(cacheKey, result);
        log.info("GitHub 仓库 {}/{} 分支 {} 导入完成，缓存 {} 个代码文件",
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
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/compare/" + sourceRef + "..." + targetRef;

        JsonNode node = httpGet(url);
        StringBuilder sb = new StringBuilder();
        JsonNode files = node.path("files");

        int skippedFiles = 0;
        int truncatedFiles = 0;
        int totalSize = 0;

        for (JsonNode file : files) {
            String filename = file.path("filename").asText();
            int changes = file.path("changes").asInt(0);

            // 1. 跳过非代码文件（lock 文件、minified、binary、generated 等）
            if (isNonCodeFile(filename)) {
                skippedFiles++;
                continue;
            }

            // 2. 跳过超大变更的文件（如整个 lock 文件重写）
            if (changes > 2000) {
                skippedFiles++;
                continue;
            }

            String patch = file.path("patch").asText("");
            if (patch.isEmpty()) {
                // 二进制或空文件，跳过
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
            String entry = "--- " + filename + " (+" + file.path("additions").asInt(0)
                    + " -" + file.path("deletions").asInt(0) + ")\n" + patch + "\n\n";
            if (totalSize + entry.length() > MAX_DIFF_TOTAL_CHARS) {
                skippedFiles++;
                continue;
            }

            sb.append(entry);
            totalSize += entry.length();
        }

        // 汇总信息
        sb.append("总计变更文件: ").append(files.size());
        if (skippedFiles > 0) {
            sb.append(" (已过滤 ").append(skippedFiles).append(" 个非代码/超大文件");
            if (truncatedFiles > 0) {
                sb.append("，截断 ").append(truncatedFiles).append(" 个过长文件");
            }
            sb.append(")");
        }
        sb.append(", ahead by ").append(node.path("ahead_by").asText("0"))
                .append(", behind by ").append(node.path("behind_by").asText("0"));
        return sb.toString();
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
        // 自动生成的文件
        if (lower.endsWith("package-lock.json") || lower.contains("autogenerated")
                || lower.contains("auto-generated")) {
            return true;
        }
        return false;
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
        // 支持: https://github.com/owner/repo, https://github.com/owner/repo/tree/branch
        // 支持: https://github.com/owner/repo.git
        String trimmed = repoUrl
                .replaceAll("https?://github\\.com/", "")
                .replaceAll("\\.git$", "")
                .replaceAll("/$", "");

        String[] parts = trimmed.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("GitHub 仓库链接格式不正确: " + repoUrl);
        }
        result.put("owner", parts[0]);
        result.put("repo", parts[1]);
        result.put("branch", parts.length >= 4 && "tree".equals(parts[2]) ? parts[3] : "");
        return result;
    }

    /**
     * GitHub API GET 请求
     * 固定携带 Authorization: token {token}
     */
    private JsonNode httpGet(String url) {
        Request.Builder builder = new Request.Builder().url(url).get();
        builder.header("Accept", "application/vnd.github.v3+json");
        builder.header("X-GitHub-Api-Version", "2022-11-28");
        if (apiToken != null && !apiToken.isEmpty()) {
            builder.header("Authorization", "token " + apiToken);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.code() == 403) {
                throw new RuntimeException("GitHub API 限流 (429/403)，请稍后重试");
            }
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("GitHub API 请求失败: " + response.code() + " - " + errBody);
            }
            String body = response.body().string();
            return objectMapper.readTree(body);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("GitHub API 请求异常: " + e.getMessage(), e);
        }
    }

    private String extractFileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
