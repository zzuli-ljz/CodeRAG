package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.dto.ChatRequest;
import com.coderag.entity.ChatHistory;
import com.coderag.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * AI 代码问答控制器
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 提交代码问答
     */
    @PostMapping
    public R<ChatHistory> ask(@Valid @RequestBody ChatRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(chatService.ask(userId, request.getRepoId(), request.getQuestion()));
    }

    /**
     * 获取问答历史
     */
    @GetMapping("/history/{repoId}")
    public R<Page<ChatHistory>> getHistory(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(chatService.getChatHistory(userId, repoId, page, size));
    }
}
