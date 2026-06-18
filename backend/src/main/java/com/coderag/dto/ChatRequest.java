package com.coderag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 问答请求
 */
@Data
public class ChatRequest {

    @NotNull(message = "仓库ID不能为空")
    private Long repoId;

    @NotBlank(message = "问题不能为空")
    private String question;
}
