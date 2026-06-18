package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.User;
import com.coderag.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户个人中心控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取个人信息
     */
    @GetMapping("/info")
    public R<User> getUserInfo(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(userService.getUserInfo(userId));
    }

    /**
     * 获取个人中心概览
     */
    @GetMapping("/profile")
    public R<Map<String, Object>> getProfile(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(userService.getProfile(userId));
    }
}
