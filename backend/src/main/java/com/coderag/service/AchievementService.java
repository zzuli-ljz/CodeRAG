package com.coderag.service;

import com.coderag.entity.LearningStreak;
import com.coderag.entity.UserAchievement;
import com.coderag.repository.CodeSnippetRepository;
import com.coderag.repository.LearningStreakRepository;
import com.coderag.repository.UserAchievementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 学习打卡 & 成就系统服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    private final LearningStreakRepository streakRepository;
    private final UserAchievementRepository achievementRepository;
    private final CodeSnippetRepository snippetRepository;

    /** 成就定义 */
    private static final Map<String, AchievementDef> ACHIEVEMENTS = new LinkedHashMap<>();
    static {
        ACHIEVEMENTS.put("FIRST_ASK", new AchievementDef("初出茅庐", "完成第一次 AI 代码问答", "💬"));
        ACHIEVEMENTS.put("FIRST_QUIZ", new AchievementDef("学海无涯", "完成第一次刷题", "📝"));
        ACHIEVEMENTS.put("FIRST_SNIPPET", new AchievementDef("收藏家", "收藏第一个代码片段", "⭐"));
        ACHIEVEMENTS.put("STREAK_3", new AchievementDef("持之以恒", "连续学习 3 天", "🔥"));
        ACHIEVEMENTS.put("STREAK_7", new AchievementDef("学习达人", "连续学习 7 天", "💪"));
        ACHIEVEMENTS.put("STREAK_30", new AchievementDef("学霸", "连续学习 30 天", "👑"));
        ACHIEVEMENTS.put("ASK_10", new AchievementDef("勤学好问", "累计提问 10 次", "❓"));
        ACHIEVEMENTS.put("ASK_50", new AchievementDef("问题大师", "累计提问 50 次", "🎯"));
        ACHIEVEMENTS.put("QUIZ_20", new AchievementDef("刷题能手", "累计刷题 20 题", "✏️"));
        ACHIEVEMENTS.put("QUIZ_100", new AchievementDef("刷题狂魔", "累计刷题 100 题", "🏆"));
        ACHIEVEMENTS.put("SNIPPET_10", new AchievementDef("代码收藏家", "收藏 10 个代码片段", "📚"));
        ACHIEVEMENTS.put("TOTAL_DAYS_7", new AchievementDef("一周之星", "累计学习 7 天", "📅"));
        ACHIEVEMENTS.put("TOTAL_DAYS_30", new AchievementDef("月度之星", "累计学习 30 天", "🌟"));
    }

    /** 记录学习活动（打卡） */
    @Transactional
    public Map<String, Object> recordActivity(Long userId) {
        LocalDate today = LocalDate.now();
        Optional<LearningStreak> existing = streakRepository.findByUserIdAndDate(userId, today);

        if (existing.isPresent()) {
            LearningStreak streak = existing.get();
            streak.setActivityCount(streak.getActivityCount() + 1);
            streakRepository.save(streak);
        } else {
            LearningStreak streak = new LearningStreak();
            streak.setUserId(userId);
            streak.setDate(today);
            streak.setActivityCount(1);
            streakRepository.save(streak);
        }

        // 先 flush 确保数据已写入
        streakRepository.flush();

        // 检查并发放成就（在事务内，但使用安全的方法）
        List<UserAchievement> newAchievements = checkAndAwardSafe(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("streak", getStreakInfo(userId));
        result.put("newAchievements", newAchievements);
        return result;
    }

    /** 获取打卡信息 */
    public Map<String, Object> getStreakInfo(Long userId) {
        long currentStreak = getCurrentStreak(userId);
        long totalDays = streakRepository.countDistinctDatesByUserId(userId);

        // 最近30天打卡日历
        LocalDate today = LocalDate.now();
        List<LocalDate> recentDates = new ArrayList<>();
        Set<LocalDate> streakDates = new HashSet<>();
        List<LearningStreak> recentStreaks = streakRepository.findByUserIdAndDateBetweenOrderByDateDesc(
                userId, today.minusDays(29), today);
        for (LearningStreak s : recentStreaks) {
            streakDates.add(s.getDate());
        }
        for (int i = 29; i >= 0; i--) {
            recentDates.add(today.minusDays(i));
        }

        Map<String, Object> info = new HashMap<>();
        info.put("currentStreak", currentStreak);
        info.put("totalDays", totalDays);
        info.put("todayChecked", streakDates.contains(today));
        info.put("recentDates", recentDates.stream()
                .map(d -> Map.of("date", d.toString(), "checked", streakDates.contains(d)))
                .toList());
        return info;
    }

    private long getCurrentStreak(Long userId) {
        // 始终使用 Java 计算，避免原生 SQL 在事务中失败导致事务 abort
        return getCurrentStreakSafe(userId);
    }

    /** 获取所有成就 */
    public List<Map<String, Object>> getAllAchievements(Long userId) {
        List<UserAchievement> earned = achievementRepository.findByUserIdOrderByEarnedAtDesc(userId);
        Set<String> earnedKeys = new HashSet<>();
        for (UserAchievement a : earned) earnedKeys.add(a.getAchievementKey());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, AchievementDef> entry : ACHIEVEMENTS.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("key", entry.getKey());
            item.put("name", entry.getValue().name);
            item.put("description", entry.getValue().description);
            item.put("icon", entry.getValue().icon);
            item.put("earned", earnedKeys.contains(entry.getKey()));
            if (earnedKeys.contains(entry.getKey())) {
                for (UserAchievement a : earned) {
                    if (a.getAchievementKey().equals(entry.getKey())) {
                        item.put("earnedAt", a.getEarnedAt());
                        break;
                    }
                }
            }
            result.add(item);
        }
        return result;
    }

    /** 检查并发放成就（安全版本，仅使用 Java 计算，避免原生 SQL 事务问题） */
    private List<UserAchievement> checkAndAwardSafe(Long userId) {
        List<UserAchievement> newAchievements = new ArrayList<>();

        // 使用纯 Java 计算连续天数，避免原生 SQL 在事务中失败
        long currentStreak = getCurrentStreakSafe(userId);
        long totalDays = streakRepository.countDistinctDatesByUserId(userId);
        long snippetCount = snippetRepository.countByUserId(userId);

        // 连续打卡成就
        if (currentStreak >= 3) tryAward(userId, "STREAK_3", newAchievements);
        if (currentStreak >= 7) tryAward(userId, "STREAK_7", newAchievements);
        if (currentStreak >= 30) tryAward(userId, "STREAK_30", newAchievements);

        // 累计天数成就
        if (totalDays >= 7) tryAward(userId, "TOTAL_DAYS_7", newAchievements);
        if (totalDays >= 30) tryAward(userId, "TOTAL_DAYS_30", newAchievements);

        // 收藏成就
        if (snippetCount >= 1) tryAward(userId, "FIRST_SNIPPET", newAchievements);
        if (snippetCount >= 10) tryAward(userId, "SNIPPET_10", newAchievements);

        return newAchievements;
    }

    /** 纯 Java 计算连续打卡天数，不依赖原生 SQL */
    private long getCurrentStreakSafe(Long userId) {
        List<LearningStreak> all = streakRepository.findByUserIdOrderByDateDesc(userId);
        if (all.isEmpty()) return 0;
        Set<LocalDate> dates = new HashSet<>();
        for (LearningStreak s : all) dates.add(s.getDate());
        LocalDate today = LocalDate.now();
        if (!dates.contains(today)) return 0;
        long count = 0;
        LocalDate d = today;
        while (dates.contains(d)) {
            count++;
            d = d.minusDays(1);
        }
        return count;
    }

    /** 尝试发放特定成就 */
    public List<UserAchievement> tryAwardByKey(Long userId, String key) {
        List<UserAchievement> result = new ArrayList<>();
        tryAward(userId, key, result);
        return result;
    }

    /**
     * 检查并发放问答相关成就（由 ChatService 调用）
     */
    @Transactional
    public List<UserAchievement> checkAndAwardChatAchievements(Long userId, long chatCount) {
        List<UserAchievement> result = new ArrayList<>();
        if (chatCount >= 1) tryAward(userId, "FIRST_ASK", result);
        if (chatCount >= 10) tryAward(userId, "ASK_10", result);
        if (chatCount >= 50) tryAward(userId, "ASK_50", result);
        return result;
    }

    /**
     * 检查并发放刷题相关成就（由 QuizService 调用）
     */
    @Transactional
    public List<UserAchievement> checkAndAwardQuizAchievements(Long userId, long quizCount) {
        List<UserAchievement> result = new ArrayList<>();
        if (quizCount >= 1) tryAward(userId, "FIRST_QUIZ", result);
        if (quizCount >= 20) tryAward(userId, "QUIZ_20", result);
        if (quizCount >= 100) tryAward(userId, "QUIZ_100", result);
        return result;
    }

    /**
     * 检查并发放收藏相关成就（由 SnippetService 调用）
     */
    @Transactional
    public List<UserAchievement> checkAndAwardSnippetAchievements(Long userId) {
        List<UserAchievement> result = new ArrayList<>();
        long snippetCount = snippetRepository.countByUserId(userId);
        if (snippetCount >= 1) tryAward(userId, "FIRST_SNIPPET", result);
        if (snippetCount >= 10) tryAward(userId, "SNIPPET_10", result);
        return result;
    }

    private void tryAward(Long userId, String key, List<UserAchievement> newAchievements) {
        if (achievementRepository.existsByUserIdAndAchievementKey(userId, key)) return;
        AchievementDef def = ACHIEVEMENTS.get(key);
        if (def == null) return;

        UserAchievement achievement = new UserAchievement();
        achievement.setUserId(userId);
        achievement.setAchievementKey(key);
        achievement.setAchievementName(def.name);
        achievement.setDescription(def.description);
        achievement.setIcon(def.icon);
        achievementRepository.save(achievement);
        newAchievements.add(achievement);
        log.info("用户 {} 获得成就: {}", userId, def.name);
    }

    private record AchievementDef(String name, String description, String icon) {}
}
