package com.coderag.controller;

import com.coderag.common.result.R;
import com.coderag.entity.UserAchievement;
import com.coderag.service.AchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学习打卡 & 成就系统控制器
 */
@RestController
@RequestMapping("/api/achievement")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    /** 记录学习活动（打卡） */
    @PostMapping("/checkin")
    public R<Map<String, Object>> checkin(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(achievementService.recordActivity(userId));
    }

    /** 获取打卡信息 */
    @GetMapping("/streak")
    public R<Map<String, Object>> getStreak(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(achievementService.getStreakInfo(userId));
    }

    /** 获取所有成就 */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> getAchievements(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return R.ok(achievementService.getAllAchievements(userId));
    }
}
