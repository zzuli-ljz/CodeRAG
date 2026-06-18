package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.AsyncTask;
import com.coderag.entity.User;
import com.coderag.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员后台控制器
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    /**
     * 用户列表
     */
    @GetMapping("/users")
    public R<Page<User>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(adminService.listUsers(page, size));
    }

    /**
     * 修改用户角色
     */
    @PutMapping("/users/{userId}/role")
    public R<User> updateUserRole(@PathVariable Long userId, @RequestParam String role) {
        return R.ok(adminService.updateUserRole(userId, role));
    }

    /**
     * 封禁/解封用户
     */
    @PutMapping("/users/{userId}/ban")
    public R<User> toggleBan(@PathVariable Long userId, @RequestParam boolean banned) {
        return R.ok(adminService.setUserBanned(userId, banned));
    }

    /**
     * 设置用户自定义配额
     */
    @PutMapping("/users/{userId}/quota")
    public R<User> setUserQuota(@PathVariable Long userId, @RequestBody Map<String, Integer> body) {
        Integer importLimit = body.get("importLimit");
        Integer chatLimit = body.get("chatLimit");
        return R.ok(adminService.setUserQuota(userId, importLimit, chatLimit));
    }

    /**
     * 全局任务队列（支持按 userId 和 status 筛选）
     */
    @GetMapping("/tasks")
    public R<Page<AsyncTask>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        return R.ok(adminService.listAllTasks(page, size, userId, status));
    }

    /**
     * 处理异常任务
     */
    @PutMapping("/tasks/{taskId}/fail")
    public R<AsyncTask> markTaskFailed(@PathVariable Long taskId, @RequestParam String reason) {
        return R.ok(adminService.markTaskFailed(taskId, reason));
    }
}
