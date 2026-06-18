package com.coderag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 刷题作答请求
 */
@Data
public class QuizAnswerRequest {

    @NotNull(message = "题目ID不能为空")
    private Long quizId;

    @NotBlank(message = "答案不能为空")
    private String userAnswer;
}
