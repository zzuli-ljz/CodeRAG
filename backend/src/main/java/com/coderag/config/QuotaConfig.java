package com.coderag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 配额配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "quota")
public class QuotaConfig {

    private QuotaLimit user = new QuotaLimit();
    private QuotaLimit premium = new QuotaLimit();

    @Data
    public static class QuotaLimit {
        private int dailyImportLimit = 3;
        private int dailyChatLimit = 20;
        private int maxRepos = 5;
        private long maxStorageMb = 100;
    }

    public QuotaLimit getLimitByRole(String role) {
        return "PREMIUM".equals(role) || "ADMIN".equals(role) ? premium : user;
    }
}
