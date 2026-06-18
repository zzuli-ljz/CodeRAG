package com.coderag.rag;

import java.util.Set;

/**
 * 代码文件过滤器
 * 过滤规则：
 * - 过滤 .git 目录
 * - 过滤图片、压缩包等二进制文件
 * - 过滤配置垃圾文件（.env、lock文件等）
 * - 仅保留各类源代码文件
 */
public final class CodeFileFilter {

    private CodeFileFilter() {}

    /** 代码文件扩展名白名单 */
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "hpp",
            "go", "rs", "rb", "php", "swift", "kt", "scala", "sh", "bash",
            "sql", "html", "css", "scss", "less", "vue", "svelte",
            "json", "yaml", "yml", "xml", "toml", "md", "txt",
            "dart", "lua", "r", "pl", "ex", "exs", "erl", "hs",
            "clj", "cljs", "coffee", "groovy", "proto", "graphql"
    );

    /** 需要排除的目录 */
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "node_modules", ".git", ".svn", ".idea", ".vscode",
            "dist", "build", "target", "out", "bin",
            "__pycache__", ".next", ".nuxt", "vendor",
            "pod", "carthage", ".gradle", ".mvn",
            ".cache", ".temp", ".tmp", "coverage",
            ".husky", ".github", ".circleci"
    );

    /** 需要排除的文件（完整文件名，小写） */
    private static final Set<String> EXCLUDED_FILES = Set.of(
            ".ds_store", "thumbs.db", "desktop.ini",
            ".gitkeep", ".npmrc", ".nvmrc", ".env",
            ".env.local", ".env.production", ".env.development",
            ".gitignore", ".dockerignore", ".editorconfig",
            "license", "license.md", "license.txt",
            "dockerfile", ".gitattributes"
    );

    /** 需要排除的文件名模式（lock 文件等） */
    private static final Set<String> EXCLUDED_PATTERNS = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            "gemfile.lock", "composer.lock", "podfile.lock",
            "poetry.lock", "cargo.lock", "mix.lock",
            ".pom.xml", "bsconfig.json"
    );

    /** 二进制/资源文件扩展名 */
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "svg", "webp",
            "mp3", "mp4", "avi", "mov", "wmv", "flv", "wav", "ogg",
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar", "war", "ear",
            "ttf", "woff", "woff2", "eot", "otf",
            "exe", "dll", "so", "dylib", "bin", "dat",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "class", "pyc", "pyo", "o", "obj", "lib", "a",
            "iso", "dmg", "apk", "ipa", "deb", "rpm"
    );

    /** 单文件大小上限：1MB（超过此大小的代码文件通常是生成文件） */
    private static final long MAX_FILE_SIZE = 1024 * 1024;

    public static boolean isCodeFile(String filePath) {
        return isCodeFile(filePath, 0);
    }

    public static boolean isCodeFile(String filePath, long fileSize) {
        if (filePath == null || filePath.isEmpty()) return false;

        String lower = filePath.toLowerCase();

        // 检查文件大小
        if (fileSize > 0 && fileSize > MAX_FILE_SIZE) return false;

        // 检查排除目录
        for (String dir : EXCLUDED_DIRS) {
            if (lower.contains("/" + dir + "/") || lower.contains("\\" + dir + "\\")) {
                return false;
            }
        }

        // 检查排除文件
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        if (EXCLUDED_FILES.contains(fileName)) return false;

        // 检查排除文件名模式
        if (EXCLUDED_PATTERNS.contains(fileName)) return false;

        // 检查扩展名
        int dotIdx = lower.lastIndexOf('.');
        if (dotIdx < 0) return false;

        String ext = lower.substring(dotIdx + 1);

        // 排除二进制文件
        if (BINARY_EXTENSIONS.contains(ext)) return false;

        // 必须在代码扩展名白名单中
        return CODE_EXTENSIONS.contains(ext);
    }

    public static boolean isBinaryFile(String filePath) {
        if (filePath == null) return false;
        int dotIdx = filePath.lastIndexOf('.');
        if (dotIdx < 0) return false;
        return BINARY_EXTENSIONS.contains(filePath.substring(dotIdx + 1).toLowerCase());
    }
}
