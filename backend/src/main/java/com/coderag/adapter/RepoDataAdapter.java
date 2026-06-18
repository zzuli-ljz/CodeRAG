package com.coderag.adapter;

import com.coderag.adapter.model.RepoFile;
import com.coderag.adapter.model.RepoInfo;

import java.util.List;
import java.util.Map;

/**
 * 仓库数据适配器接口
 * 适配器模式：统一 GitHub / Gitee 双平台 API 差异
 */
public interface RepoDataAdapter {

    /**
     * 识别是否为该平台的仓库链接
     */
    boolean supports(String repoUrl);

    /**
     * 获取仓库元信息
     */
    RepoInfo fetchRepoInfo(String repoUrl);

    /**
     * 获取仓库文件树（仅结构，不含内容）
     */
    List<RepoFile> fetchFileTree(String repoUrl, String branch);

    /**
     * 获取单个文件内容（base64 自动解码）
     */
    String fetchFileContent(String repoUrl, String branch, String filePath);

    /**
     * 递归获取仓库所有代码文件（含内容）
     * 自动过滤非代码文件，结果缓存，批量拉取限流
     */
    List<RepoFile> fetchAllCodeFiles(String repoUrl, String branch);

    /**
     * 获取两个引用之间的 diff
     */
    String fetchDiff(String repoUrl, String sourceRef, String targetRef);

    /**
     * 获取仓库分支列表
     */
    List<String> fetchBranches(String repoUrl);

    /**
     * 解析仓库 URL，提取 owner、repo、branch
     */
    Map<String, String> parseRepoUrl(String repoUrl);
}
