package com.coderag.service;

import com.coderag.config.QuotaConfig;
import com.coderag.entity.QuotaUsage;
import com.coderag.entity.User;
import com.coderag.exception.BusinessException;
import com.coderag.repository.CodeRepositoryRepository;
import com.coderag.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务 - 个人中心
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final QuotaService quotaService;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final QuotaConfig quotaConfig;

    /**
     * 获取用户信息
     */
    public User getUserInfo(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    /**
     * 获取个人中心概览（配额、仓库统计）
     */
    public Map<String, Object> getProfile(Long userId) {
        User user = getUserInfo(userId);
        QuotaUsage todayUsage = quotaService.getTodayUsage(userId);
        long repoCount = codeRepositoryRepository.countByUserId(userId);

        // 使用有效配额：优先自定义配额，否则角色默认
        int dailyImportLimit = quotaService.getEffectiveImportLimit(userId);
        int dailyChatLimit = quotaService.getEffectiveChatLimit(userId);
        QuotaConfig.QuotaLimit roleLimit = quotaService.getLimit(userId);

        Map<String, Object> profile = new HashMap<>();
        profile.put("user", user);
        profile.put("todayUsage", todayUsage);
        // 返回一个包含有效配额值的 limit 对象，前端通过 dailyImportLimit/dailyChatLimit 读取
        Map<String, Object> effectiveLimit = new HashMap<>();
        effectiveLimit.put("dailyImportLimit", dailyImportLimit);
        effectiveLimit.put("dailyChatLimit", dailyChatLimit);
        effectiveLimit.put("maxRepos", roleLimit.getMaxRepos());
        profile.put("quotaLimit", effectiveLimit);
        profile.put("repoCount", repoCount);
        profile.put("maxRepos", roleLimit.getMaxRepos());
        return profile;
    }
}
