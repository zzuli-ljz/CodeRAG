package com.coderag.service;

import com.coderag.config.QuotaConfig;
import com.coderag.entity.QuotaUsage;
import com.coderag.entity.User;
import com.coderag.exception.BusinessException;
import com.coderag.repository.CodeRepositoryRepository;
import com.coderag.repository.QuotaUsageRepository;
import com.coderag.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 配额服务 - 全局限流 & 配额管理
 * 限制单用户每日仓库导入次数、AI 问答调用次数
 * 支持管理员为单个用户设置自定义配额上限
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private static final ZoneId ZONE_BEIJING = ZoneId.of("Asia/Shanghai");

    private final QuotaUsageRepository quotaUsageRepository;
    private final UserRepository userRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final QuotaConfig quotaConfig;

    /**
     * 检查用户是否被封禁
     */
    public void checkBanned(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (Boolean.TRUE.equals(user.getBanned())) {
            throw new BusinessException("您的账号已被管理员封禁，无法使用此功能");
        }
    }

    /**
     * 检查仓库导入配额（含封禁检查）
     */
    public void checkImportQuota(Long userId) {
        checkBanned(userId);
        int limit = getEffectiveImportLimit(userId);
        QuotaUsage usage = getTodayUsage(userId);

        if (usage.getImportCount() >= limit) {
            throw new BusinessException("今日仓库导入次数已达上限（" + limit + "次），请明天再试");
        }
    }

    /**
     * 检查 AI 问答配额（含封禁检查）
     */
    public void checkChatQuota(Long userId) {
        checkBanned(userId);
        int limit = getEffectiveChatLimit(userId);
        QuotaUsage usage = getTodayUsage(userId);

        if (usage.getChatCount() >= limit) {
            throw new BusinessException("今日 AI 问答次数已达上限（" + limit + "次），请明天再试");
        }
    }

    /**
     * 检查仓库数量限制
     */
    public void checkRepoLimit(Long userId) {
        checkBanned(userId);
        QuotaConfig.QuotaLimit limit = getLimit(userId);
        long currentCount = codeRepositoryRepository.countByUserId(userId);
        if (currentCount >= limit.getMaxRepos()) {
            throw new BusinessException("仓库数量已达上限（" + limit.getMaxRepos() + "个）");
        }
    }

    /**
     * 增加导入计数
     */
    public void incrementImportCount(Long userId) {
        QuotaUsage usage = getTodayUsage(userId);
        usage.setImportCount(usage.getImportCount() + 1);
        quotaUsageRepository.save(usage);
    }

    /**
     * 增加问答计数
     */
    public void incrementChatCount(Long userId) {
        QuotaUsage usage = getTodayUsage(userId);
        usage.setChatCount(usage.getChatCount() + 1);
        quotaUsageRepository.save(usage);
    }

    /**
     * 获取今日配额使用情况（北京时间）
     */
    public QuotaUsage getTodayUsage(Long userId) {
        LocalDate today = LocalDate.now(ZONE_BEIJING);
        return quotaUsageRepository.findByUserIdAndDate(userId, today)
                .orElseGet(() -> {
                    QuotaUsage usage = new QuotaUsage();
                    usage.setUserId(userId);
                    usage.setDate(today);
                    usage.setImportCount(0);
                    usage.setChatCount(0);
                    usage.setStorageUsedBytes(0L);
                    return quotaUsageRepository.save(usage);
                });
    }

    /**
     * 获取用户配额限制（角色默认）
     */
    public QuotaConfig.QuotaLimit getLimit(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return quotaConfig.getLimitByRole(user.getRole());
    }

    /**
     * 获取用户有效的导入配额上限
     * 优先使用管理员设置的自定义配额，否则使用角色默认配额
     */
    public int getEffectiveImportLimit(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.getCustomImportLimit() != null && user.getCustomImportLimit() >= 0) {
            return user.getCustomImportLimit();
        }
        return getLimit(userId).getDailyImportLimit();
    }

    /**
     * 获取用户有效的 AI 问答配额上限
     * 优先使用管理员设置的自定义配额，否则使用角色默认配额
     */
    public int getEffectiveChatLimit(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.getCustomChatLimit() != null && user.getCustomChatLimit() >= 0) {
            return user.getCustomChatLimit();
        }
        return getLimit(userId).getDailyChatLimit();
    }
}
