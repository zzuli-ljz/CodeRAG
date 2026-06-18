package com.coderag.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 缓存定时清理任务
 * 每小时清理一次过期缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionScheduler {

    private final CacheService cacheService;

    @Scheduled(fixedRate = 3600000) // 每小时执行
    public void evictExpiredCache() {
        int evicted = cacheService.evictExpired();
        if (evicted > 0) {
            log.info("缓存清理完成，清除 {} 条过期缓存，当前缓存总数: {}", evicted, cacheService.size());
        }
    }
}
