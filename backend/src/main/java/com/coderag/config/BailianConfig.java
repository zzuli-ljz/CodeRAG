package com.coderag.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 火山引擎方舟 AI 配置（兼容 OpenAI 格式）
 * - 对话模型走火山方舟：doubao-seed-2.0-mini（最低价方案：输入0.2/输出2.0 元/百万token）
 * - Embedding 模型走百炼：text-embedding-v4（避免重建向量库，API Key 通过 DASHSCOPE_API_KEY 环境变量）
 * - 对话 API Key：ARK_API_KEY 环境变量
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "dashscope")
public class BailianConfig {

    /** 方舟 API Key（从环境变量 ARK_API_KEY 读取） */
    private String apiKey;

    /** 火山方舟 OpenAI 兼容 Base URL（对话用） */
    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";

    /** 对话模型：doubao-seed-2-0-mini-260428（输入0.2/输出2.0 元/百万token） */
    private String model = "doubao-seed-2-0-mini-260428";

    /** 向量化模型：保留百炼 text-embedding-v4（避免重建向量库） */
    private String embeddingModel = "text-embedding-v4";

    /** Embedding 专用 Base URL（百炼 DashScope，与对话分离） */
    private String embeddingBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** Embedding 专用 API Key（从环境变量 DASHSCOPE_API_KEY 读取，与对话分离） */
    private String embeddingApiKey;

    /** 最大输出 token（提升到 8192，适应架构分析等长输出场景） */
    private int maxTokens = 8192;

    /** 向量维度（text-embedding-v4 默认 1024） */
    private int embeddingDimension = 1024;

    /** 额度耗尽自动拦截标志 */
    private volatile boolean quotaExhausted = false;

    @PostConstruct
    public void validate() {
        // ── 诊断日志：直接读取系统环境变量，排查 Spring 属性绑定问题 ──
        String envArkKey = System.getenv("ARK_API_KEY");
        String envDashKey = System.getenv("DASHSCOPE_API_KEY");
        log.info("【诊断】 System.getenv(ARK_API_KEY) = {}",
                envArkKey == null ? "null" :
                (envArkKey.length() > 8 ? envArkKey.substring(0, 4) + "****" + envArkKey.substring(envArkKey.length() - 4) : "too_short:" + envArkKey.length()));
        log.info("【诊断】 System.getenv(DASHSCOPE_API_KEY) = {}",
                envDashKey == null ? "null" :
                (envDashKey.length() > 8 ? envDashKey.substring(0, 4) + "****" + envDashKey.substring(envDashKey.length() - 4) : "too_short:" + envDashKey.length()));
        log.info("【诊断】 Spring 绑定 apiKey 前缀={}, 长度={}",
                apiKey == null ? "null" : (apiKey.length() >= 4 ? apiKey.substring(0, 4) : apiKey),
                apiKey == null ? 0 : apiKey.length());

        // ── 兜底逻辑：如果 Spring 绑定的 apiKey 不以 ark- 开头，直接从环境变量读取 ──
        if (apiKey == null || apiKey.isBlank() || !apiKey.startsWith("ark-")) {
            log.warn("⚠️ Spring 绑定的 apiKey 不正确（非 ark- 开头），尝试直接从 System.getenv(ARK_API_KEY) 读取");
            if (envArkKey != null && !envArkKey.isBlank() && envArkKey.startsWith("ark-")) {
                apiKey = envArkKey;
                log.info("✅ 已从 System.getenv 恢复正确的 ARK_API_KEY");
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("""
                    ╔══════════════════════════════════════════════╗
                    ║  ⚠️  ARK_API_KEY 未配置！                     ║
                    ║  AI 对话功能将无法使用！                      ║
                    ║                                              ║
                    ║  解决方法：                                   ║
                    ║  在环境变量中设置:                            ║
                    ║    export ARK_API_KEY=your-ark-api-key        ║
                    ║                                              ║
                    ║  获取地址:                                    ║
                    ║  https://console.volcengine.com/ark/          ║
                    ╚══════════════════════════════════════════════╝""");
        } else {
            String masked = apiKey.length() > 8
                    ? apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4)
                    : "****";
            log.info("火山方舟 AI 配置已加载, model={}, apiKey={}", model, masked);
        }

        if (embeddingApiKey == null || embeddingApiKey.isBlank()) {
            log.warn("""
                    ╔══════════════════════════════════════════════╗
                    ║  ⚠️  DASHSCOPE_API_KEY 未配置！               ║
                    ║  Embedding 向量化功能将无法使用！             ║
                    ║                                              ║
                    ║  解决方法：                                   ║
                    ║  在环境变量中设置:                            ║
                    ║    export DASHSCOPE_API_KEY=sk-xxxxxxxx       ║
                    ║                                              ║
                    ║  获取地址:                                    ║
                    ║  https://bailian.console.aliyun.com/         ║
                    ╚══════════════════════════════════════════════╝""");
        } else {
            String masked = embeddingApiKey.length() > 8
                    ? embeddingApiKey.substring(0, 4) + "****" + embeddingApiKey.substring(embeddingApiKey.length() - 4)
                    : "****";
            log.info("百炼 Embedding 配置已加载, embeddingModel={}, embeddingApiKey={}", embeddingModel, masked);
        }
    }

    public void markQuotaExhausted() {
        this.quotaExhausted = true;
    }

    public boolean isQuotaExhausted() {
        return this.quotaExhausted;
    }

    public void resetQuotaExhausted() {
        this.quotaExhausted = false;
    }
}
