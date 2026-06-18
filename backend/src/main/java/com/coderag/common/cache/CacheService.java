package com.coderag.common.cache;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多级内存缓存服务
 * - 仓库代码缓存：避免重复调用第三方 API
 * - 问答结果缓存：减少 token 消耗
 * - 向量缓存：避免重复 embedding 计算
 */
@Slf4j
@Service
public class CacheService {

    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();

    /** 问答结果缓存时长：24小时 */
    private static final long CHAT_CACHE_TTL_MINUTES = 24 * 60;
    /** 仓库代码缓存时长：24小时 */
    private static final long REPO_CACHE_TTL_MINUTES = 24 * 60;
    /** 向量缓存时长：7天 */
    private static final long VECTOR_CACHE_TTL_MINUTES = 7 * 24 * 60;

    @Data
    private static class CacheEntry<T> {
        private T value;
        private long expireAt;

        CacheEntry(T value, long ttlMinutes) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttlMinutes * 60 * 1000;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    public <T> Optional<T> get(String key) {
        CacheEntry<?> entry = cache.get(key);
        if (entry == null) return Optional.empty();
        if (entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        T value = (T) entry.getValue();
        return Optional.of(value);
    }

    public <T> void put(String key, T value, long ttlMinutes) {
        cache.put(key, new CacheEntry<>(value, ttlMinutes));
    }

    public <T> void put(String key, T value) {
        put(key, value, CHAT_CACHE_TTL_MINUTES);
    }

    public void putRepoCache(String key, Object value) {
        put(key, value, REPO_CACHE_TTL_MINUTES);
    }

    public void putVectorCache(String key, float[] value) {
        put(key, value, VECTOR_CACHE_TTL_MINUTES);
    }

    public void putChatCache(String key, String value) {
        put(key, value, CHAT_CACHE_TTL_MINUTES);
    }

    public void remove(String key) {
        cache.remove(key);
    }

    public void removeByPrefix(String prefix) {
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * 清理所有过期缓存
     */
    public int evictExpired() {
        int[] count = {0};
        cache.entrySet().removeIf(e -> {
            if (e.getValue().isExpired()) {
                count[0]++;
                return true;
            }
            return false;
        });
        return count[0];
    }

    /**
     * 缓存统计
     */
    public int size() {
        return cache.size();
    }

    // ====== Key 生成工具 ======

    public static String repoKey(String platform, String owner, String repo, String branch) {
        return "repo:" + platform + ":" + owner + "/" + repo + ":" + branch;
    }

    public static String chatKey(Long repoId, String question) {
        return "chat:" + repoId + ":" + sha256Short(question);
    }

    public static String architectureKey(Long repoId) {
        return "arch:" + repoId;
    }

    public static String vectorKey(String text) {
        return "vec:" + sha256Short(text);
    }

    /**
     * SHA-256 短摘要（16字符），比 hashCode 更稳定、碰撞率更低
     */
    private static String sha256Short(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
