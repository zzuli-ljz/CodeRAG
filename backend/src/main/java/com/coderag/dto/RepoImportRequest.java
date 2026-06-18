package com.coderag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 仓库导入请求
 */
@Data
public class RepoImportRequest {

    @NotBlank(message = "仓库链接不能为空")
    private String repoUrl;

    private String branch;
}
