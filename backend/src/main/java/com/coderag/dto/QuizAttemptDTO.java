package com.coderag.dto;

import com.coderag.entity.QuizAttempt;
import com.coderag.entity.QuizQuestion;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作答记录 DTO，包含题目信息
 */
@Data
public class QuizAttemptDTO {

    private Long id;
    private Long userId;
    private Long quizId;
    private String userAnswer;
    private Boolean isCorrect;
    private String aiFeedback;
    private String status;
    private LocalDateTime createdAt;

    // 题目信息
    private String question;
    private String options;
    private String correctAnswer;
    private String explanation;
    private String difficulty;
    private String codeSnippet;
    private String knowledgePoint;

    // 仓库信息
    private Long repoId;
    private String repoName;

    public static QuizAttemptDTO from(QuizAttempt attempt, QuizQuestion question, String repoName) {
        QuizAttemptDTO dto = new QuizAttemptDTO();
        dto.setId(attempt.getId());
        dto.setUserId(attempt.getUserId());
        dto.setQuizId(attempt.getQuizId());
        dto.setUserAnswer(attempt.getUserAnswer());
        dto.setIsCorrect(attempt.getIsCorrect());
        dto.setAiFeedback(attempt.getAiFeedback());
        dto.setStatus(attempt.getStatus());
        dto.setCreatedAt(attempt.getCreatedAt());

        if (question != null) {
            dto.setQuestion(question.getQuestion());
            dto.setOptions(question.getOptions());
            dto.setCorrectAnswer(question.getAnswer());
            dto.setExplanation(question.getExplanation());
            dto.setDifficulty(question.getDifficulty());
            dto.setCodeSnippet(question.getCodeSnippet());
            dto.setKnowledgePoint(question.getKnowledgePoint());
            dto.setRepoId(question.getRepoId());
            dto.setRepoName(repoName);
        }

        return dto;
    }
}
