package com.coderag.adapter.model;

import lombok.Data;

/**
 * 仓库元信息
 */
@Data
public class RepoInfo {

    private String name;
    private String owner;
    private String description;
    private String defaultBranch;
    private String language;
    private Long size;
    private String htmlUrl;
}
