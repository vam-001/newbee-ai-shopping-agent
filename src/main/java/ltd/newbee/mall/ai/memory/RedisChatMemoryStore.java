package ltd.newbee.mall.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis 的会话记忆存储。
 *
 * <p>使用 LangChain4j 官方的 ChatMessageSerializer 做 JSON 序列化；
 * Redis 不可用时自动降级到进程内存，保证聊天功能不被基础设施故障拖垮。</p>
 */
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryStore.class);
    private static final String KEY_PREFIX = "ai:memory:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final InMemoryChatMemoryStore fallback = new InMemoryChatMemoryStore();

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate, int ttlDays) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofDays(Math.max(1, ttlDays));
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = key(memoryId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return ChatMessageDeserializer.messagesFromJson(json);
            }
        } catch (Exception e) {
            log.warn("Redis chat memory read failed, fallback to in-memory: {}", e.getMessage());
        }
        return fallback.getMessages(memoryId);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 始终维护一份内存副本，Redis 故障时仍能继续对话
        fallback.updateMessages(memoryId, messages);
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            redisTemplate.opsForValue().set(key(memoryId), json, ttl);
        } catch (Exception e) {
            log.warn("Redis chat memory write failed, keep in-memory copy: {}", e.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        fallback.deleteMessages(memoryId);
        try {
            redisTemplate.delete(key(memoryId));
        } catch (Exception e) {
            log.warn("Redis chat memory delete failed: {}", e.getMessage());
        }
    }

    private String key(Object memoryId) {
        return KEY_PREFIX + String.valueOf(memoryId);
    }
}
