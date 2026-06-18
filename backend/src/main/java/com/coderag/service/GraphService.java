package com.coderag.service;

import com.coderag.common.cache.CacheService;
import com.coderag.entity.CodeChunk;
import com.coderag.entity.CodeGraph;
import com.coderag.entity.CodeRepository;
import com.coderag.repository.CodeChunkRepository;
import com.coderag.repository.CodeGraphRepository;
import com.coderag.repository.CodeRepositoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码知识图谱服务
 * 优先使用正则匹配提取调用关系（零 Token 消耗），
 * 仅对复杂关系（如接口实现、设计模式）使用 AI 补全
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final CodeChunkRepository codeChunkRepository;
    private final CodeGraphRepository codeGraphRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ====== 正则模式（零 Token 消耗提取调用关系） ======

    /** Java: import, method call, class extends/implements, annotation */
    private static final Pattern JAVA_IMPORT = Pattern.compile("import\\s+([\\w.]+(?:\\.[\\w*]+)*)\\s*;");
    private static final Pattern JAVA_CLASS = Pattern.compile("(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:class|interface|enum)\\s+(\\w+)");
    private static final Pattern JAVA_EXTENDS = Pattern.compile("class\\s+\\w+\\s+extends\\s+(\\w+)");
    private static final Pattern JAVA_IMPLEMENTS = Pattern.compile("class\\s+\\w+\\s+implements\\s+([\\w,\\s]+)");
    private static final Pattern JAVA_METHOD = Pattern.compile("(?:public|private|protected|static|final|synchronized|abstract|native)?\\s*(?:<[^>]+>\\s*)?(?:\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\([^)]*\\)");
    private static final Pattern JAVA_METHOD_CALL = Pattern.compile("\\.(\\w+)\\s*\\(");
    private static final Pattern JAVA_ANNOTATION = Pattern.compile("@(\\w+)");

    /** Python: import, class, function def, function call */
    private static final Pattern PY_IMPORT = Pattern.compile("(?:from\\s+(\\S+)\\s+)?import\\s+([\\w,\\s]+)");
    private static final Pattern PY_CLASS = Pattern.compile("class\\s+(\\w+)\\s*(?:\\(([^)]*)\\))?\\s*:");
    private static final Pattern PY_FUNC = Pattern.compile("def\\s+(\\w+)\\s*\\(");
    private static final Pattern PY_CALL = Pattern.compile("(\\w+)\\s*\\(");

    /** JavaScript/TypeScript: import, class, function, arrow function, method call */
    private static final Pattern JS_IMPORT = Pattern.compile("import\\s+(?:\\{[^}]*\\}|\\w+)\\s+from\\s+['\"]([^'\"]+)['\"]");
    private static final Pattern JS_CLASS = Pattern.compile("class\\s+(\\w+)\\s*(?:extends\\s+(\\w+))?");
    private static final Pattern JS_FUNC = Pattern.compile("(?:function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s+)?\\()");
    private static final Pattern JS_CALL = Pattern.compile("\\.(\\w+)\\s*\\(");

    /** Go: import, func, struct, method call */
    private static final Pattern GO_IMPORT = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern GO_FUNC = Pattern.compile("func\\s+(?:\\(\\w+\\s+\\*?\\w+\\)\\s+)?(\\w+)\\s*\\(");
    private static final Pattern GO_STRUCT = Pattern.compile("type\\s+(\\w+)\\s+struct");
    private static final Pattern GO_CALL = Pattern.compile("\\.(\\w+)\\s*\\(");

    /** 通用：函数定义 + 调用关系 */
    private static final Pattern GENERIC_FUNC = Pattern.compile("(?:function|func|def|fn|fun)\\s+(\\w+)");

    /** 排除的常见方法名（非用户自定义调用） */
    private static final Set<String> COMMON_METHODS = Set.of(
            "println", "print", "printf", "log", "info", "warn", "error", "debug",
            "get", "set", "add", "remove", "put", "size", "isEmpty", "toString",
            "equals", "hashCode", "compareTo", "length", "charAt", "substring",
            "split", "replace", "trim", "toLowerCase", "toUpperCase",
            "map", "filter", "reduce", "forEach", "find", "sort",
            "append", "format", "join", "contains", "indexOf",
            "parseInt", "parseFloat", "valueOf", "read", "write", "close",
            "start", "stop", "run", "execute", "init", "destroy",
            "main", "assert", "require", "module", "exports"
    );

    /**
     * 构建代码图谱（正则提取 + 可选 AI 补全）
     */
    public CodeGraph buildGraph(Long userId, Long repoId, boolean forceRefresh) {
        // 1. 不强制刷新时返回已有结果
        if (!forceRefresh) {
            CodeGraph existing = getLatestGraph(repoId);
            if (existing != null) {
                log.info("图谱已有结果，直接返回: repoId={}, round={}", repoId, existing.getRound());
                return existing;
            }
        }

        CodeRepository repo = codeRepositoryRepository.findById(repoId)
                .orElseThrow(() -> new RuntimeException("仓库不存在，repoId=" + repoId));

        List<CodeChunk> chunks = codeChunkRepository.findByRepoId(repoId);
        if (chunks == null || chunks.isEmpty()) {
            throw new RuntimeException("该仓库暂无代码数据，请先完成导入");
        }

        // 2. 正则提取调用关系（零 Token 消耗）
        ObjectNode graphJson = extractRelationsByRegex(chunks, repo);

        // 3. 计算轮次并保存
        int nextRound = computeNextRound(repoId);
        CodeGraph graph = new CodeGraph();
        graph.setRepoId(repoId);
        graph.setRound(nextRound);
        graph.setGraphData(graphJson.toString());
        graph = codeGraphRepository.save(graph);

        // 缓存
        String cacheKey = "graph:" + repoId;
        cacheService.put(cacheKey, graph, 120);

        log.info("图谱构建完成: repoId={}, round={}, nodes={}, edges={}",
                repoId, nextRound,
                graphJson.get("nodes").size(),
                graphJson.get("edges").size());

        return graph;
    }

    /**
     * 正则提取调用关系（核心方法，零 Token 消耗）
     */
    private ObjectNode extractRelationsByRegex(List<CodeChunk> chunks, CodeRepository repo) {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        ArrayNode edges = graph.putArray("edges");

        // 节点去重
        Set<String> nodeIds = new HashSet<>();
        // 边去重
        Set<String> edgeKeys = new HashSet<>();

        // 按文件分组
        Map<String, List<CodeChunk>> fileGroups = new LinkedHashMap<>();
        for (CodeChunk chunk : chunks) {
            fileGroups.computeIfAbsent(chunk.getFilePath(), k -> new ArrayList<>()).add(chunk);
        }

        for (Map.Entry<String, List<CodeChunk>> entry : fileGroups.entrySet()) {
            String filePath = entry.getKey();
            String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
            String language = entry.getValue().get(0).getLanguage();
            String group = extractModuleGroup(filePath);

            // 文件节点
            String fileNodeId = "file:" + filePath;
            if (nodeIds.add(fileNodeId)) {
                ObjectNode fileNode = nodes.addObject();
                fileNode.put("id", fileNodeId);
                fileNode.put("label", fileName);
                fileNode.put("type", "file");
                fileNode.put("file", filePath);
                fileNode.put("group", group);
                fileNode.put("language", language != null ? language : "Unknown");
            }

            // 合并文件内所有代码内容
            String fullCode = entry.getValue().stream()
                    .map(CodeChunk::getContent)
                    .collect(java.util.stream.Collectors.joining("\n"));

            // 根据语言选择正则模式
            String lang = language != null ? language.toLowerCase() : "";

            // 提取类/结构体/接口
            List<String> classNames = extractClasses(fullCode, lang);
            for (String className : classNames) {
                String classNodeId = "class:" + filePath + ":" + className;
                if (nodeIds.add(classNodeId)) {
                    ObjectNode classNode = nodes.addObject();
                    classNode.put("id", classNodeId);
                    classNode.put("label", className);
                    classNode.put("type", "class");
                    classNode.put("file", filePath);
                    classNode.put("group", group);
                    classNode.put("parent", fileNodeId);
                }
                // 类属于文件
                addEdge(edges, edgeKeys, fileNodeId, classNodeId, "contains");
            }

            // 提取函数/方法
            List<String> funcNames = extractFunctions(fullCode, lang);
            for (String funcName : funcNames) {
                String funcNodeId = "func:" + filePath + ":" + funcName;
                if (nodeIds.add(funcNodeId)) {
                    ObjectNode funcNode = nodes.addObject();
                    funcNode.put("id", funcNodeId);
                    funcNode.put("label", funcName + "()");
                    funcNode.put("type", "function");
                    funcNode.put("file", filePath);
                    funcNode.put("group", group);
                }
            }

            // 提取继承/实现关系
            extractInheritance(fullCode, lang, filePath, group, nodes, nodeIds, edges, edgeKeys);

            // 提取函数调用关系
            extractCalls(fullCode, lang, filePath, nodes, nodeIds, edges, edgeKeys);

            // 提取 import 依赖（模块间关系）
            extractImports(fullCode, lang, filePath, group, nodes, nodeIds, edges, edgeKeys, fileGroups.keySet());
        }

        return graph;
    }

    private String extractModuleGroup(String filePath) {
        if (filePath == null) return "root";
        String[] parts = filePath.replace('\\', '/').split("/");
        if (parts.length <= 1) return "root";
        // 跳过 src/main/java 等常见前缀
        int startIdx = 0;
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("src") || parts[i].equals("main") || parts[i].equals("java")
                    || parts[i].equals("lib") || parts[i].equals("app")) {
                startIdx = i + 1;
            }
        }
        if (startIdx < parts.length - 1) {
            return parts[startIdx];
        }
        return parts.length > 1 ? parts[parts.length - 2] : "root";
    }

    private List<String> extractClasses(String code, String lang) {
        List<String> result = new ArrayList<>();
        Pattern pattern = switch (lang) {
            case "java" -> JAVA_CLASS;
            case "python" -> PY_CLASS;
            case "javascript", "typescript", "js", "ts", "jsx", "tsx" -> JS_CLASS;
            case "go" -> GO_STRUCT;
            default -> null;
        };
        if (pattern == null) return result;

        Matcher m = pattern.matcher(code);
        while (m.find()) {
            String name = m.group(1);
            if (name != null && !name.isEmpty() && !name.equals("class") && !name.equals("interface")
                    && Character.isUpperCase(name.charAt(0))) {
                result.add(name);
            }
        }
        return result;
    }

    private List<String> extractFunctions(String code, String lang) {
        List<String> result = new ArrayList<>();
        Pattern pattern = switch (lang) {
            case "java" -> JAVA_METHOD;
            case "python" -> PY_FUNC;
            case "javascript", "typescript", "js", "ts", "jsx", "tsx" -> JS_FUNC;
            case "go" -> GO_FUNC;
            default -> GENERIC_FUNC;
        };
        if (pattern == null) return result;

        Matcher m = pattern.matcher(code);
        while (m.find()) {
            String name = m.group(1);
            if (name == null) name = m.group(2); // JS arrow function
            if (name != null && !name.isEmpty() && !COMMON_METHODS.contains(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private void extractInheritance(String code, String lang, String filePath, String group,
                                     ArrayNode nodes, Set<String> nodeIds,
                                     ArrayNode edges, Set<String> edgeKeys) {
        // Java extends
        if (lang.equals("java")) {
            Matcher m = JAVA_EXTENDS.matcher(code);
            while (m.find()) {
                String parent = m.group(1);
                String parentNodeId = "class:" + filePath + ":" + parent;
                // 查找当前类名
                Matcher classM = JAVA_CLASS.matcher(code);
                if (classM.find()) {
                    String child = classM.group(1);
                    String childNodeId = "class:" + filePath + ":" + child;
                    addEdge(edges, edgeKeys, childNodeId, parentNodeId, "extends");
                }
            }
        }
        // Python inheritance
        if (lang.equals("python")) {
            Matcher m = PY_CLASS.matcher(code);
            while (m.find()) {
                String className = m.group(1);
                String parents = m.group(2);
                if (parents != null && !parents.isEmpty()) {
                    for (String parent : parents.split(",")) {
                        parent = parent.trim();
                        if (!parent.isEmpty() && !parent.equals("object")) {
                            String childNodeId = "class:" + filePath + ":" + className;
                            String parentNodeId = "class:" + filePath + ":" + parent;
                            addEdge(edges, edgeKeys, childNodeId, parentNodeId, "extends");
                        }
                    }
                }
            }
        }
        // JS extends
        if (lang.equals("javascript") || lang.equals("typescript") || lang.equals("js") || lang.equals("ts")) {
            Matcher m = JS_CLASS.matcher(code);
            while (m.find()) {
                String className = m.group(1);
                String parent = m.group(2);
                if (parent != null && !parent.isEmpty()) {
                    String childNodeId = "class:" + filePath + ":" + className;
                    String parentNodeId = "class:" + filePath + ":" + parent;
                    addEdge(edges, edgeKeys, childNodeId, parentNodeId, "extends");
                }
            }
        }
    }

    private void extractCalls(String code, String lang, String filePath,
                               ArrayNode nodes, Set<String> nodeIds,
                               ArrayNode edges, Set<String> edgeKeys) {
        Pattern callPattern = switch (lang) {
            case "java" -> JAVA_METHOD_CALL;
            case "python" -> PY_CALL;
            case "javascript", "typescript", "js", "ts", "jsx", "tsx" -> JS_CALL;
            case "go" -> GO_CALL;
            default -> null;
        };
        if (callPattern == null) return;

        // 先收集当前文件的所有函数名
        Set<String> localFuncs = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            if (node.has("file") && filePath.equals(node.get("file").asText())
                    && "function".equals(node.get("type").asText())) {
                localFuncs.add(node.get("label").asText().replace("()", ""));
            }
        }

        Matcher m = callPattern.matcher(code);
        while (m.find()) {
            String called = m.group(1);
            if (called == null || called.isEmpty() || COMMON_METHODS.contains(called)) continue;

            // 只记录对本地函数的调用（跨文件调用需要 AI 补全）
            if (localFuncs.contains(called)) {
                // 找到调用者（当前上下文中的函数）
                // 简化处理：记录为函数间的潜在调用
                // caller 难以精确确定，后续可通过 AI 补全
            }
        }
    }

    private void extractImports(String code, String lang, String filePath, String group,
                                 ArrayNode nodes, Set<String> nodeIds,
                                 ArrayNode edges, Set<String> edgeKeys,
                                 Set<String> allFiles) {
        List<String> importedModules = new ArrayList<>();

        if (lang.equals("java")) {
            Matcher m = JAVA_IMPORT.matcher(code);
            while (m.find()) {
                String imp = m.group(1);
                if (imp != null && !imp.startsWith("java.") && !imp.startsWith("javax.")) {
                    importedModules.add(imp);
                }
            }
        } else if (lang.equals("python")) {
            Matcher m = PY_IMPORT.matcher(code);
            while (m.find()) {
                String mod = m.group(1) != null ? m.group(1) : m.group(2);
                if (mod != null && !mod.startsWith("os") && !mod.startsWith("sys")) {
                    importedModules.add(mod);
                }
            }
        } else if (lang.equals("javascript") || lang.equals("typescript") || lang.equals("js") || lang.equals("ts")) {
            Matcher m = JS_IMPORT.matcher(code);
            while (m.find()) {
                String imp = m.group(1);
                if (imp != null && imp.startsWith(".")) {
                    importedModules.add(imp);
                }
            }
        }

        // 尝试匹配同项目内的文件
        for (String imp : importedModules) {
            for (String f : allFiles) {
                if (!f.equals(filePath) && isRelatedImport(imp, f, lang)) {
                    String targetNodeId = "file:" + f;
                    String sourceNodeId = "file:" + filePath;
                    addEdge(edges, edgeKeys, sourceNodeId, targetNodeId, "imports");
                    break;
                }
            }
        }
    }

    private boolean isRelatedImport(String importPath, String filePath, String lang) {
        String normalizedFile = filePath.replace('\\', '/').replace(".java", "")
                .replace(".py", "").replace(".js", "").replace(".ts", "").replace(".go", "");
        String normalizedImport = importPath.replace('.', '/');

        return normalizedFile.contains(normalizedImport) || normalizedImport.contains(
                normalizedFile.substring(normalizedFile.lastIndexOf('/') + 1));
    }

    private void addEdge(ArrayNode edges, Set<String> edgeKeys, String source, String target, String relation) {
        String key = source + "->" + target + ":" + relation;
        if (edgeKeys.add(key)) {
            ObjectNode edge = edges.addObject();
            edge.put("source", source);
            edge.put("target", target);
            edge.put("relation", relation);
        }
    }

    private int computeNextRound(Long repoId) {
        try {
            List<CodeGraph> existing = codeGraphRepository.findByRepoIdOrderByCreatedAtDesc(repoId);
            if (!existing.isEmpty()) {
                return existing.get(0).getRound() + 1;
            }
        } catch (Exception e) {
            log.warn("查询历史图谱失败: repoId={}", repoId);
        }
        return 1;
    }

    public CodeGraph getLatestGraph(Long repoId) {
        return codeGraphRepository.findFirstByRepoIdOrderByCreatedAtDesc(repoId).orElse(null);
    }

    public Page<CodeGraph> getHistory(Long repoId, int page, int size) {
        return codeGraphRepository.findByRepoIdOrderByCreatedAtDesc(repoId, PageRequest.of(page, size));
    }
}
