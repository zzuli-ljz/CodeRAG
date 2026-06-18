package com.coderag.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * 数据库迁移初始化器
 * 项目启动后自动执行 init.sql，创建全部数据表
 * 使用 IF NOT EXISTS 保证幂等，可安全重复执行
 *
 * 本地 dev profile（H2）下自动跳过（H2 不支持 pgvector 语法）
 * 线上 PostgreSQL 环境下正常执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final MigrationProperties migrationProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void initDatabase() {
        if (!migrationProperties.isEnabled()) {
            log.info("数据库迁移已禁用（当前为本地开发 H2 模式，跳过 pgvector 迁移）");
            return;
        }

        try {
            log.info("开始执行数据库迁移脚本（PostgreSQL + pgvector）...");

            ClassPathResource resource = new ClassPathResource("db/init.sql");
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

            // 按分号分割，逐行清理注释后执行
            String[] rawStatements = sql.split(";");
            int count = 0;
            for (String stmt : rawStatements) {
                // 移除每行开头的注释行
                StringBuilder sb = new StringBuilder();
                for (String line : stmt.split("\\n")) {
                    String t = line.trim();
                    if (!t.startsWith("--") && !t.isEmpty()) {
                        sb.append(line).append('\n');
                    }
                }
                String trimmed = sb.toString().trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    jdbcTemplate.execute(trimmed);
                    count++;
                    log.info("SQL 执行成功: {}", trimmed.substring(0, Math.min(80, trimmed.length())));
                } catch (Exception e) {
                    if (e.getMessage() != null &&
                            (e.getMessage().contains("already exists") || e.getMessage().contains("已存在"))) {
                        log.debug("表/索引已存在，跳过: {}", trimmed.substring(0, Math.min(60, trimmed.length())));
                    } else {
                        log.warn("SQL 执行失败 [{}]: {}", e.getMessage().replaceAll("[\\n\\r]", " "),
                                trimmed.substring(0, Math.min(120, trimmed.length())));
                    }
                }
            }

            log.info("数据库迁移完成，成功执行 {} 条语句", count);

            // 执行 Schema 升级（处理已存在表的结构变更）
            upgradeSchema();

        } catch (Exception e) {
            log.error("数据库迁移失败", e);
            throw new RuntimeException("数据库迁移失败: " + e.getMessage(), e);
        }
    }

    /**
     * Schema 版本升级：对已存在的旧表执行 ALTER TABLE
     * 每次表结构变更都在这里追加对应的升级 SQL（使用幂等写法）
     */
    private void upgradeSchema() {
        log.info("开始 Schema 版本升级检查...");
        String[] upgrades = {
            // --- architecture_analyses: 去掉 UNIQUE 约束 + 加 round 列 ---
            "ALTER TABLE architecture_analyses DROP CONSTRAINT IF EXISTS architecture_analyses_repo_id_key",
            "ALTER TABLE architecture_analyses ADD COLUMN IF NOT EXISTS round INTEGER DEFAULT 1",
            // --- code_chunks: 清空旧维度数据后升级到 1024 维（text-embedding-v4）---
            "DELETE FROM code_chunks",
            "ALTER TABLE code_chunks ALTER COLUMN embedding TYPE vector(1024)",
            "DEALLOCATE ALL",
            // --- v2 新增表: 代码知识图谱 ---
            "CREATE TABLE IF NOT EXISTS code_graphs (id BIGSERIAL PRIMARY KEY, repo_id BIGINT NOT NULL, round INTEGER DEFAULT 1, graph_data TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
            "CREATE INDEX IF NOT EXISTS idx_code_graphs_repo_id ON code_graphs(repo_id)",
            // --- v2 新增表: 代码翻译历史 ---
            "CREATE TABLE IF NOT EXISTS translation_histories (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, repo_id BIGINT NOT NULL, source_file_path VARCHAR(500), source_lang VARCHAR(30) NOT NULL, target_lang VARCHAR(30) NOT NULL, source_code TEXT, translated_code TEXT NOT NULL, diff_notes TEXT, caveats TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
            "CREATE INDEX IF NOT EXISTS idx_translation_histories_repo_id ON translation_histories(repo_id)",
            "CREATE INDEX IF NOT EXISTS idx_translation_histories_user_id ON translation_histories(user_id)",
            // --- v3 用户表新增字段: 封禁状态 + 自定义配额 ---
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS banned BOOLEAN NOT NULL DEFAULT false",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS custom_import_limit INTEGER",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS custom_chat_limit INTEGER",
        };
        for (String sql : upgrades) {
            try {
                jdbcTemplate.execute(sql);
                log.info("Schema 升级成功: {}", sql.substring(0, Math.min(80, sql.length())));
            } catch (Exception e) {
                // 幂等忽略已知错误
                String msg = e.getMessage().replaceAll("[\\n\\r]", " ");
                if (msg.contains("does not exist") || msg.contains(" already exists")) {
                    log.debug("Schema 升级跳过（已满足）: {}", sql.substring(0, Math.min(60, sql.length())));
                } else {
                    log.warn("Schema 升级警告 [{}]: {}", msg, sql.substring(0, Math.min(80, sql.length())));
                }
            }
        }
        // 驱逐 HikariCP 连接池中所有连接，避免 cached plan must not change result type
        evictConnectionPool();
        log.info("Schema 版本升级检查完成");
    }

    /**
     * 驱逐 HikariCP 连接池中的所有连接，强制重建
     * ALTER TABLE 改列类型后，PostgreSQL 的 prepared statement 缓存会失效，
     * 需要清除连接池中的旧连接，让新连接使用新的执行计划。
     */
    private void evictConnectionPool() {
        try {
            if (dataSource instanceof HikariDataSource hikariDs) {
                log.info("正在驱逐 HikariCP 连接池中的 {} 个活跃连接...",
                        hikariDs.getHikariPoolMXBean().getActiveConnections());
                hikariDs.getHikariPoolMXBean().softEvictConnections();
                log.info("HikariCP 连接池驱逐完成");
            } else {
                log.info("DataSource 不是 HikariCP，跳过连接池驱逐");
            }
        } catch (Exception e) {
            log.warn("驱逐连接池失败（非致命）: {}", e.getMessage());
        }
    }

    /**
     * 迁移开关配置
     * 线上默认 true，本地 dev profile 通过 application-dev.yml 设为 false
     */
    @Configuration
    @ConfigurationProperties(prefix = "app.db-migration")
    @RequiredArgsConstructor
    public static class MigrationProperties {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
