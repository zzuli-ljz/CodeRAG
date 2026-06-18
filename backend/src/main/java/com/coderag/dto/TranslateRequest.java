package com.coderag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 代码翻译请求 DTO
 */
@Data
public class TranslateRequest {

    /** 仓库ID（翻译仓库内文件时必填） */
    private Long repoId;

    /** 源文件路径（翻译仓库内文件时必填） */
    private String filePath;

    /** 源代码（翻译代码片段时必填，与 filePath 二选一） */
    private String sourceCode;

    /** 源语言（可选，自动检测） */
    private String sourceLang;

    /** 目标语言（必填） */
    @NotBlank(message = "目标语言不能为空")
    private String targetLang;
}
