package ltd.newbee.mall.ai.service;

import ltd.newbee.mall.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 基于 Redis 的固定窗口限流，Redis 不可用时降级为本地滑动窗口。
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String KEY_PREFIX = "ai:rate:";

    private final StringRedisTemplate redisTemplate;
    private final AiProperties properties;
    private final ConcurrentHashMap<String, Deque<Long>> localWindows = new ConcurrentHashMap<>();

    public RateLimitService(StringRedisTemplate redisTemplate, AiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean tryAcquire(String clientKey) {
        int limit = Math.max(1, properties.getRateLimitPerMinute());
        long minute = System.currentTimeMillis() / 60_000;
        String key = KEY_PREFIX + clientKey + ":" + minute;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(65));
            }
            return count != null && count <= limit;
        } catch (Exception e) {
            log.warn("Redis rate limit failed, fallback to local window: {}", e.getMessage());
        }
        return tryAcquireLocal(clientKey, limit);
    }

    private boolean tryAcquireLocal(String clientKey, int limit) {
        long now = System.currentTimeMillis();
        Deque<Long> window = localWindows.computeIfAbsent(clientKey, k -> new ConcurrentLinkedDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > 60_000) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                return false;
            }
            window.addLast(now);
            if (localWindows.size() > 10_000) {
                localWindows.clear();
            }
            return true;
        }
    }
}
