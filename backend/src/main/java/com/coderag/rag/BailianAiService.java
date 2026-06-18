package com.coderag.rag;

import com.coderag.common.cache.CacheService;
import com.coderag.config.BailianConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 火山引擎方舟 AI 服务
 * - 对话模型：doubao-seed-2.0-mini（火山方舟原生 /api/v3/responses 端点）
 * - 向量化模型：text-embedding-v4（百炼 DashScope，避免重建向量库）
 * - 全局 ARK_API_KEY 环境变量鉴权
 * - 额度耗尽自动拦截，杜绝扣费
 * - 多级缓存减少 token 消耗
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BailianAiService {

    private final BailianConfig bailianConfig;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(80, TimeUnit.SECONDS)
            .build();

    /** 单次对话最大输入字符数（system + user 总长，防止 token 爆炸） */
    private static final int MAX_INPUT_CHARS = 30_000;

    /**
     * 通用对话 - 用于代码问答、架构分析、版本解读、习题生成
     * 模型：doubao-seed-2.0-mini（输入0.2/输出2.0 元/百万token）
     * 使用火山方舟原生 /api/v3/responses 端点（官方文档确认格式）
     *
     * 官方请求格式：
     *   POST https://ark.cn-beijing.volces.com/api/v3/responses
     *   Body: {"model": "xxx", "input": [{"role": "user", "content": [...]}]}
     *
     * 官方响应格式：
     *   {"output": [{"type": "message", "content": [{"type": "text", "text": "回复内容"}]}]}
     */
    public String chat(String systemPrompt, String userMessage) {
        // 配额耗尽拦截
        if (bailianConfig.isQuotaExhausted()) {
            throw new RuntimeException("AI 免费额度已耗尽，请检查火山方舟控制台");
        }

        // 输入长度保护：截断过长 prompt
        String safeSystem = systemPrompt;
        String safeUser = userMessage;
        int totalInputLen = (systemPrompt != null ? systemPrompt.length() : 0)
                + (userMessage != null ? userMessage.length() : 0);
        if (totalInputLen > MAX_INPUT_CHARS) {
            log.warn("AI 输入过长 ({} 字符)，将截断: system={}, user={}",
                    totalInputLen,
                    systemPrompt != null ? systemPrompt.length() : 0,
                    userMessage != null ? userMessage.length() : 0);
            int userLen = userMessage != null ? userMessage.length() : 0;
            int sysBudget = MAX_INPUT_CHARS - userLen - 200;
            if (sysBudget > 500 && systemPrompt != null && systemPrompt.length() > sysBudget) {
                safeSystem = systemPrompt.substring(0, sysBudget)
                        + "\n\n... (上下文过长已截断)";
            } else if (sysBudget <= 500 && userMessage != null && userMessage.length() > MAX_INPUT_CHARS - 200) {
                safeUser = userMessage.substring(0, MAX_INPUT_CHARS - 200)
                        + "\n\n... (输入过长已截断)";
            }
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", bailianConfig.getModel());

            // ── 构建符合火山方舟 /api/v3/responses 的 input 数组 ──
            // 支持多消息：system + user（官方文档格式）
            ArrayNode inputArray = requestBody.putArray("input");

            if (safeSystem != null && !safeSystem.isEmpty()) {
                ObjectNode sysMsg = inputArray.addObject();
                sysMsg.put("role", "system");
                // system 消息用纯字符串 content（官方文档示例格式）
                sysMsg.put("content", safeSystem);
            }

            ObjectNode userMsg = inputArray.addObject();
            userMsg.put("role", "user");
            // user 消息用 content 数组（支持后续扩展 multimodal）
            ArrayNode contentArray = userMsg.putArray("content");
            ObjectNode textContent = contentArray.addObject();
            textContent.put("type", "input_text");
            textContent.put("text", safeUser != null ? safeUser : "");

            // 关闭思考模式（doubao-seed 默认开启，会返回 reasoning 思考过程而非最终答案）
            ObjectNode thinking = requestBody.putObject("thinking");
            thinking.put("type", "disabled");

            String json = objectMapper.writeValueAsString(requestBody);
            String url = bailianConfig.getBaseUrl() + "/responses";

            log.info("【AI请求】 url={}, model={}, apiKey前缀={}",
                    url, bailianConfig.getModel(),
                    maskKey(bailianConfig.getApiKey()));

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + bailianConfig.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String rawBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("【AI调用失败】 status={}, body={}", response.code(), rawBody);

                    if (rawBody.contains("quota") || rawBody.contains("Insufficient")
                            || rawBody.contains("billing") || response.code() == 402
                            || rawBody.contains("余额") || rawBody.contains("额度")) {
                        bailianConfig.markQuotaExhausted();
                        throw new RuntimeException("AI 免费额度已耗尽，已自动拦截后续请求");
                    }
                    throw new RuntimeException("AI 服务调用失败(HTTP " + response.code() + "): " + rawBody);
                }

                // ── 诊断日志：打印原始响应 ──
                String preview = rawBody.length() > 1000 ? rawBody.substring(0, 1000) + "...[截断]" : rawBody;
                log.info("【AI响应】 status={}, 长度={}, 内容: {}", response.code(), rawBody.length(), preview);

                JsonNode result = objectMapper.readTree(rawBody);

                // ── 响应解析：遍历 output 数组，优先取 message（最终答案），跳过 reasoning（思考过程） ──
                String content = "";

                JsonNode outputNode = result.path("output");
                if (outputNode.isArray() && !outputNode.isEmpty()) {
                    String reasoningContent = "";  // 备用：如果只有 reasoning 没有 message

                    for (int i = 0; i < outputNode.size(); i++) {
                        JsonNode item = outputNode.get(i);
                        String itemType = item.path("type").asText("");

                        if ("message".equals(itemType)) {
                            // ★ 最终答案：从 content[] 中提取
                            JsonNode contents = item.path("content");
                            if (contents.isArray() && !contents.isEmpty()) {
                                for (JsonNode c : contents) {
                                    String t = c.path("text").asText("");
                                    if (!t.isBlank()) { content = t; break; }
                                }
                            } else if (!contents.isMissingNode() && contents.isTextual()) {
                                content = contents.asText("");
                            }
                        } else if ("reasoning".equals(itemType)) {
                            // 思考过程摘要（备用）
                            reasoningContent = item.path("summary").path("text").asText("");
                            if (reasoningContent.isBlank()) {
                                reasoningContent = deepExtractText(item);
                            }
                        } else {
                            // 其他未知类型
                            String other = extractTextFromItem(item);
                            if (!other.isBlank() && content.isBlank()) {
                                content = other;
                            }
                        }

                        // 找到 message 就不再继续
                        if (!content.isBlank()) break;
                    }

                    // 兜底：如果没有任何 message 元素但有 reasoning，使用 reasoning 内容
                    if (content.isBlank() && !reasoningContent.isBlank()) {
                        log.warn("【AI响应】未找到 message 类型元素，回退到 reasoning 摘要");
                        content = reasoningContent;
                    }

                    // 终极兜底：深度搜索
                    if (content.isBlank()) {
                        content = deepExtractText(outputNode);
                    }
                } else if (outputNode.isObject()) {
                    content = deepExtractText(outputNode);
                }

                // 终极兜底
                if (content.isBlank()) {
                    content = result.path("output").path(0).path("content").path(0).path("text").asText("");
                }

                // 处理思考模式标签
                content = safeStripThink(content);

                if (content == null || content.isBlank()) {
                    log.error("【AI响应解析失败】提取到空文本！原始响应: {}", preview);
                    throw new RuntimeException("AI 返回了空响应。原始数据: " + preview);
                }

                log.info("【AI成功】 回复长度={}", content.length());
                return content;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("火山方舟 AI 调用异常", e);
            throw new RuntimeException("AI 服务异常: " + e.getMessage());
        }
    }

    /** 安全脱敏 API Key（只显示前后4位） */
    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return key == null ? "(null)" : "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    /**
     * 从单个 output 元素中提取文本
     */
    private String extractTextFromItem(JsonNode item) {
        // 尝试 content[].text
        JsonNode contents = item.path("content");
        if (contents.isArray()) {
            for (JsonNode c : contents) {
                String t = c.path("text").asText("");
                if (!t.isBlank()) return t;
            }
        }
        return "";
    }

    /**中第一个非空 text 字段值
     * 兜底解析：当已知路径都匹配不到时，深度遍历查找
     */
    private String deepExtractText(JsonNode node) {
        if (node == null || node.isMissingNode()) return "";
        // 直接有 text 字段
        String direct = node.path("text").asText("");
        if (!direct.isBlank()) return direct;
        // 检查 summary.text
        String summary = node.path("summary").path("text").asText("");
        if (!summary.isBlank()) return summary;
        // 遍历子节点（数组或对象）
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = deepExtractText(child);
                if (!found.isBlank()) return found;
            }
        } else if (node.isObject()) {
            var it = node.fields();
            while (it.hasNext()) {
                var entry = it.next();
                // 跳过 id/role/type 等元数据字段，只深入内容字段
                String key = entry.getKey().toLowerCase();
                if (key.equals("id") || key.equals("role") || key.equals("type")
                        || key.equals("model") || key.equals("created_at")
                        || key.equals("object") || key.equals("status")
                        || key.equals("usage")) continue;
                String found = deepExtractText(entry.getValue());
                if (!found.isBlank()) return found;
            }
        }
        return "";
    }

    /**
     * 安全去除思考模式标签
     * 如果去除后为空则保留原文（防止模型将全部内容放入标签内）
     */
    private String safeStripThink(String content) {
        if (content == null || content.isEmpty()) return content;
        try {
            String stripped = content.replaceAll("(?s)\\{1}.*?\\{1}\\s*", "").trim();
            if (stripped.isEmpty()) {
                log.warn("stripThink 后为空，返回原始内容（可能全部在标签内）");
                return content.trim();
            }
            return stripped;
        } catch (Exception e) {
            log.warn("stripThink 正则处理异常，返回原始内容: {}", e.getMessage());
            return content.trim();
        }
    }

    /**
     * 文本向量化 - 用于 RAG embedding
     * 模型：text-embedding-v4（百炼，1024 维）
     */
    public float[] embed(String text) {
        if (bailianConfig.isQuotaExhausted()) {
            throw new RuntimeException("AI 免费额度已耗尽，请检查火山方舟控制台");
        }

        String cacheKey = CacheService.vectorKey(text);
        Optional<float[]> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", bailianConfig.getEmbeddingModel());
            ArrayNode inputArray = requestBody.putArray("input");
            inputArray.add(text);

            String json = objectMapper.writeValueAsString(requestBody);
            String url = bailianConfig.getEmbeddingBaseUrl() + "/embeddings";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + bailianConfig.getEmbeddingApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "";
                    log.error("Embedding 调用失败: code={}, body={}", response.code(), errBody);

                    boolean isFreeTierExhausted = errBody.contains("FreeTierOnly")
                            || errBody.contains("free tier")
                            || (errBody.contains("quota") || errBody.contains("Insufficient")
                                || errBody.contains("billing") || response.code() == 402);
                    if (isFreeTierExhausted) {
                        bailianConfig.markQuotaExhausted();
                    }

                    if (errBody.contains("FreeTierOnly")) {
                        throw new RuntimeException(
                            "【Embedding 免费额度已耗尽】\n\n" +
                            "模型 " + bailianConfig.getEmbeddingModel() + " 的免费调用次数已用完。\n" +
                            "解决方法：\n" +
                            "1. 登录 https://bailian.console.aliyun.com/\n" +
                            "2. 进入「模型广场」→ 找到 text-embedding-v4 → 关闭「仅使用免费额度」开关");
                    }

                    if (isFreeTierExhausted) {
                        throw new RuntimeException("AI 服务配额不足（HTTP " + response.code() + "）");
                    }
                    throw new RuntimeException("Embedding 调用失败(HTTP " + response.code() + "): " + errBody);
                }
                JsonNode result = objectMapper.readTree(response.body().string());

                JsonNode embeddingNode = result.path("data").path(0).path("embedding");
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }

                cacheService.putVectorCache(cacheKey, embedding);
                return embedding;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding 调用异常", e);
            throw new RuntimeException("Embedding 服务异常: " + e.getMessage());
        }
    }

    /**
     * 批量文本向量化
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        List<Integer> uncachedIndices = new ArrayList<>();
        List<String> uncachedTexts = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String cacheKey = CacheService.vectorKey(texts.get(i));
            Optional<float[]> cached = cacheService.get(cacheKey);
            if (cached.isPresent()) {
                results.add(cached.get());
            } else {
                results.add(null);
                uncachedIndices.add(i);
                uncachedTexts.add(texts.get(i));
            }
        }

        if (uncachedTexts.isEmpty()) {
            log.debug("embedBatch: 全部命中缓存");
            return results;
        }

        int batchSize = 10;
        for (int batchStart = 0; batchStart < uncachedTexts.size(); batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, uncachedTexts.size());
            List<String> batchTexts = uncachedTexts.subList(batchStart, batchEnd);
            List<Integer> batchIndices = uncachedIndices.subList(batchStart, batchEnd);

            List<float[]> batchEmbeddings = embedBatchApiCall(batchTexts);

            for (int j = 0; j < batchEmbeddings.size(); j++) {
                int idx = batchIndices.get(j);
                results.set(idx, batchEmbeddings.get(j));
                cacheService.putVectorCache(CacheService.vectorKey(texts.get(idx)), batchEmbeddings.get(j));
            }

            if (batchEnd < uncachedTexts.size()) {
                try { Thread.sleep(300); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        }

        log.info("embedBatch: 共{}条，缓存命中{}条，API调用{}条",
                texts.size(), texts.size() - uncachedTexts.size(), uncachedTexts.size());
        return results;
    }

    private List<float[]> embedBatchApiCall(List<String> texts) {
        if (bailianConfig.isQuotaExhausted()) {
            throw new RuntimeException("AI 免费额度已耗尽");
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", bailianConfig.getEmbeddingModel());
            ArrayNode arr = body.putArray("input");
            for (String t : texts) arr.add(t);

            String url = bailianConfig.getEmbeddingBaseUrl() + "/embeddings";

            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + bailianConfig.getEmbeddingApiKey())
                    .post(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String eb = resp.body() != null ? resp.body().string() : "";
                    handleEmbeddingError(resp.code(), eb);
                }

                JsonNode r = objectMapper.readTree(resp.body().string());
                JsonNode data = r.path("data");
                List<float[]> embs = new ArrayList<>(texts.size());
                for (int i = 0; i < texts.size(); i++) {
                    JsonNode en = data.path(i).path("embedding");
                    float[] emb = new float[en.size()];
                    for (int j = 0; j < emb.length; j++) emb[j] = (float) en.get(j).asDouble();
                    embs.add(emb);
                }
                return embs;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量 Embedding 异常", e);
            throw new RuntimeException("异常: " + e.getMessage());
        }
    }

    private void handleEmbeddingError(int code, String body) {
        boolean exhausted = body.contains("FreeTierOnly") || body.contains("quota")
                || body.contains("Insufficient") || body.contains("billing") || code == 402;
        if (exhausted) bailianConfig.markQuotaExhausted();

        if (body.contains("FreeTierOnly"))
            throw new RuntimeException("Embedding 免费额度已耗尽，请到百炼控制台开通按量付费");
        if (exhausted)
            throw new RuntimeException("配额不足(HTTP " + code + ")");
        throw new RuntimeException("Embedding 失败(HTTP " + code + "): " + body);
    }
}
