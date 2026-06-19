-- ============================================================
-- CodeRAG 数据库初始化脚本
-- 启动时由 DatabaseMigrationInitializer 自动执行
-- 适配 Neon PostgreSQL + pgvector 扩展
-- ============================================================

-- 1. 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- -----------------------------------------------------------
-- 2. 用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    avatar          VARCHAR(500),
    banned          BOOLEAN      NOT NULL DEFAULT false,
    custom_import_limit INTEGER,
    custom_chat_limit   INTEGER,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------
-- 3. 仓库信息表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_repositories (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    platform        VARCHAR(20)   NOT NULL,
    repo_url        VARCHAR(500)  NOT NULL,
    repo_name       VARCHAR(200)  NOT NULL,
    repo_owner      VARCHAR(200),
    description     VARCHAR(1000),
    default_branch  VARCHAR(100),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    file_count      INTEGER,
    code_line_count BIGINT,
    storage_bytes   BIGINT,
    language        VARCHAR(50),
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_code_repos_user_id ON code_repositories(user_id);

-- -----------------------------------------------------------
-- 4. 异步导入任务表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS async_tasks (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    repo_id         BIGINT,
    task_type       VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    progress        INTEGER      DEFAULT 0,
    status_message  VARCHAR(1000),
    result          TEXT,
    error_message   TEXT,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_async_tasks_user_id ON async_tasks(user_id);

-- -----------------------------------------------------------
-- 5. 代码向量块表（pgvector 1024 维，余弦相似度检索）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_chunks (
    id          BIGSERIAL PRIMARY KEY,
    repo_id     BIGINT        NOT NULL,
    file_path   VARCHAR(500),
    language    VARCHAR(100),
    content     TEXT          NOT NULL,
    summary     TEXT,
    start_line  INTEGER,
    end_line    INTEGER,
    embedding   vector(1024),   -- text-embedding-v4 输出 1024 维向量
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- 向量检索索引（余弦距离）
CREATE INDEX IF NOT EXISTS idx_code_chunks_embedding ON code_chunks
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 常用查询索引
CREATE INDEX IF NOT EXISTS idx_code_chunks_repo_id ON code_chunks(repo_id);

-- -----------------------------------------------------------
-- 6. AI 问答记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_histories (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    repo_id          BIGINT NOT NULL,
    question         TEXT   NOT NULL,
    answer           TEXT   NOT NULL,
    source_snippets  TEXT,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_histories_user_repo ON chat_histories(user_id, repo_id);

-- -----------------------------------------------------------
-- 7. 系统配置表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_configs (
    id          BIGSERIAL PRIMARY KEY,
    config_key  VARCHAR(200) NOT NULL UNIQUE,
    config_value TEXT        NOT NULL,
    description VARCHAR(500),
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认系统配置
INSERT INTO system_configs (config_key, config_value, description)
VALUES
    ('ark_quota_exhausted', 'false', '火山方舟 API 额度是否耗尽'),
    ('system_maintenance_mode', 'false', '系统维护模式开关'),
    ('max_repo_size_mb', '100', '单仓库最大导入大小(MB)')
ON CONFLICT (config_key) DO NOTHING;

-- -----------------------------------------------------------
-- 8. 用户每日配额使用表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS quota_usages (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT    NOT NULL,
    date               DATE      NOT NULL,
    import_count       INTEGER   NOT NULL DEFAULT 0,
    chat_count         INTEGER   NOT NULL DEFAULT 0,
    storage_used_bytes BIGINT    DEFAULT 0,
    UNIQUE(user_id, date)
);

CREATE INDEX IF NOT EXISTS idx_quota_usages_user_date ON quota_usages(user_id, date);

-- -----------------------------------------------------------
-- 9. 内置管理员账户（用户名: admin / 密码: admin123）
-- -----------------------------------------------------------
INSERT INTO users (username, email, password_hash, role)
VALUES (
    'admin',
    'admin@coderag.com',
    '$2a$12$hp5.9NdmS6fole0IFzEinuJj2ji4qZta8n7ch3WnUkLtQ5WuScc/2',
    'ADMIN'
) ON CONFLICT (username) DO NOTHING;

-- -----------------------------------------------------------
-- 10. 刷题题目表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS quiz_questions (
    id              BIGSERIAL PRIMARY KEY,
    repo_id         BIGINT        NOT NULL,
    question        TEXT          NOT NULL,
    options         TEXT,
    answer          TEXT          NOT NULL,
    explanation     TEXT,
    difficulty      VARCHAR(20),
    code_snippet    TEXT,
    knowledge_point VARCHAR(100),
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quiz_questions_repo_id ON quiz_questions(repo_id);

-- -----------------------------------------------------------
-- 11. 刷题作答记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS quiz_attempts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    quiz_id         BIGINT        NOT NULL,
    user_answer     TEXT          NOT NULL,
    is_correct      BOOLEAN       NOT NULL,
    ai_feedback     TEXT,
    status          VARCHAR(20)   NOT NULL DEFAULT 'NORMAL',
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quiz_attempts_user_id ON quiz_attempts(user_id);
CREATE INDEX IF NOT EXISTS idx_quiz_attempts_quiz_id ON quiz_attempts(quiz_id);
CREATE INDEX IF NOT EXISTS idx_quiz_attempts_user_status ON quiz_attempts(user_id, status);

-- -----------------------------------------------------------
-- 12. 代码版本对比记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS version_comparisons (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    repo_id         BIGINT        NOT NULL,
    source_ref      VARCHAR(200)  NOT NULL,
    target_ref      VARCHAR(200)  NOT NULL,
    diff_content    TEXT,
    analysis_result TEXT,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_version_comparisons_user_repo ON version_comparisons(user_id, repo_id);

-- -----------------------------------------------------------
-- 13. 架构分析结果持久化表（支持多版本历史记录）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS architecture_analyses (
    id              BIGSERIAL PRIMARY KEY,
    repo_id         BIGINT        NOT NULL,
    round           INTEGER       DEFAULT 1,
    analysis_result TEXT          NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_arch_analyses_repo_id ON architecture_analyses(repo_id);

-- -----------------------------------------------------------
-- 14. 代码知识图谱表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_graphs (
    id              BIGSERIAL PRIMARY KEY,
    repo_id         BIGINT        NOT NULL,
    round           INTEGER       DEFAULT 1,
    graph_data      TEXT          NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_code_graphs_repo_id ON code_graphs(repo_id);

-- -----------------------------------------------------------
-- 15. 代码翻译历史表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS translation_histories (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    repo_id          BIGINT        NOT NULL,
    source_file_path VARCHAR(500),
    source_lang      VARCHAR(30)   NOT NULL,
    target_lang      VARCHAR(30)   NOT NULL,
    source_code      TEXT,
    translated_code  TEXT          NOT NULL,
    diff_notes       TEXT,
    caveats          TEXT,
    created_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_translation_histories_repo_id ON translation_histories(repo_id);
CREATE INDEX IF NOT EXISTS idx_translation_histories_user_id ON translation_histories(user_id);

-- -----------------------------------------------------------
-- 16. 代码片段收藏表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_snippets (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    repo_id         BIGINT        NOT NULL,
    file_path       VARCHAR(500),
    language        VARCHAR(100),
    content         TEXT          NOT NULL,
    title           VARCHAR(200),
    note            TEXT,
    tags            VARCHAR(500),
    start_line      INTEGER,
    end_line        INTEGER,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_code_snippets_user_id ON code_snippets(user_id);
CREATE INDEX IF NOT EXISTS idx_code_snippets_user_repo ON code_snippets(user_id, repo_id);

-- -----------------------------------------------------------
-- 17. 学习打卡记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS learning_streaks (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    date            DATE          NOT NULL,
    activity_count  INTEGER       NOT NULL DEFAULT 1,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, date)
);

CREATE INDEX IF NOT EXISTS idx_learning_streaks_user_date ON learning_streaks(user_id, date);

-- -----------------------------------------------------------
-- 18. 用户成就表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_achievements (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    achievement_key VARCHAR(50)   NOT NULL,
    achievement_name VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    icon            VARCHAR(50),
    earned_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, achievement_key)
);

CREATE INDEX IF NOT EXISTS idx_user_achievements_user_id ON user_achievements(user_id);

-- -----------------------------------------------------------
-- 19. 代码学习路径表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS learning_paths (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    repo_id         BIGINT        NOT NULL,
    round           INTEGER       DEFAULT 1,
    path_content    TEXT          NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_learning_paths_user_repo ON learning_paths(user_id, repo_id);

-- -----------------------------------------------------------
-- 20. 编程挑战表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_challenges (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT        NOT NULL,
    repo_id              BIGINT        NOT NULL,
    file_path            VARCHAR(500),
    challenge_description TEXT         NOT NULL,
    code_template        TEXT          NOT NULL,
    reference_code       TEXT          NOT NULL,
    language             VARCHAR(100),
    difficulty           VARCHAR(50),
    created_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_code_challenges_repo_id ON code_challenges(repo_id);
CREATE INDEX IF NOT EXISTS idx_code_challenges_user_repo ON code_challenges(user_id, repo_id);

-- -----------------------------------------------------------
-- 21. 编程挑战提交记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS challenge_submissions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    challenge_id    BIGINT        NOT NULL,
    submitted_code  TEXT          NOT NULL,
    score           INTEGER,
    feedback        TEXT,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_challenge_submissions_user_challenge ON challenge_submissions(user_id, challenge_id);

-- -----------------------------------------------------------
-- 22. 笔记版本历史表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS note_versions (
    id              BIGSERIAL PRIMARY KEY,
    snippet_id      BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    note            TEXT,
    tags            VARCHAR(500),
    version_number  INTEGER       NOT NULL,
    version_label   VARCHAR(100),
    summary         VARCHAR(200),
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_note_versions_snippet ON note_versions(snippet_id, version_number DESC);
CREATE INDEX IF NOT EXISTS idx_note_versions_user ON note_versions(user_id);

-- -----------------------------------------------------------
-- 23. 代码时间线分析记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS code_timelines (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    repo_id         BIGINT        NOT NULL,
    round           INTEGER       DEFAULT 1,
    timeline_data   TEXT,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_code_timelines_user_repo ON code_timelines(user_id, repo_id);
