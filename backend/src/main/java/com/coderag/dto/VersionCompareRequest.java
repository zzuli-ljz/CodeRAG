package com.coderag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 版本对比请求
 */
@Data
public class VersionCompareRequest {

    @NotNull(message = "仓库ID不能为空")
    private Long repoId;

    @NotBlank(message = "源分支/commit不能为空")
    private String sourceRef;

    @NotBlank(message = "目标分支/commit不能为空")
    private String targetRef;
}
