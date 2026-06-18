package com.coderag.adapter.model;

import lombok.Data;

/**
 * 仓库文件信息
 */
@Data
public class RepoFile {

    private String path;
    private String name;
    private boolean directory;
    private Long size;
    private String downloadUrl;

    /** 文件内容（base64 已解码为 UTF-8 字符串） */
    private String content;

    /** 文件编程语言（由扩展名推断） */
    private String language;

    /**
     * 根据文件扩展名推断编程语言
     */
    public String inferLanguage() {
        if (path == null) return "";
        String lower = path.toLowerCase();
        int dotIdx = lower.lastIndexOf('.');
        if (dotIdx < 0) return "";
        String ext = lower.substring(dotIdx + 1);
        return switch (ext) {
            case "java" -> "Java";
            case "py" -> "Python";
            case "js", "jsx" -> "JavaScript";
            case "ts", "tsx" -> "TypeScript";
            case "c", "h" -> "C";
            case "cpp", "hpp", "cc" -> "C++";
            case "go" -> "Go";
            case "rs" -> "Rust";
            case "rb" -> "Ruby";
            case "php" -> "PHP";
            case "swift" -> "Swift";
            case "kt", "kts" -> "Kotlin";
            case "scala" -> "Scala";
            case "sh", "bash" -> "Shell";
            case "sql" -> "SQL";
            case "html" -> "HTML";
            case "css", "scss", "less" -> "CSS";
            case "vue" -> "Vue";
            case "svelte" -> "Svelte";
            case "xml" -> "XML";
            case "json" -> "JSON";
            case "yaml", "yml" -> "YAML";
            case "toml" -> "TOML";
            case "md" -> "Markdown";
            default -> ext.toUpperCase();
        };
    }
}
