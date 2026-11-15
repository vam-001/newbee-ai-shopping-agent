package ltd.newbee.mall.ai.service;

import ltd.newbee.mall.ai.agent.ShoppingAssistant;
import ltd.newbee.mall.ai.agent.ShoppingAssistantFactory;
import ltd.newbee.mall.ai.config.AiProperties;
import ltd.newbee.mall.ai.entity.AiChatLog;
import ltd.newbee.mall.dao.AiChatLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * AI 导购聊天编排：输入校验、限流、Agent 创建、审计日志。
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
    private static final int QUESTION_LIMIT = 1000;
    private static final int ANSWER_LIMIT = 2000;

    private final ShoppingAssistantFactory assistantFactory;
    private final PromptGuard promptGuard;
    private final RateLimitService rateLimitService;
    private final AiChatLogMapper chatLogMapper;
    private final AiProperties properties;

    public AiChatService(ShoppingAssistantFactory assistantFactory,
                         PromptGuard promptGuard,
                         RateLimitService rateLimitService,
                         AiChatLogMapper chatLogMapper,
                         AiProperties properties) {
        this.assistantFactory = assistantFactory;
        this.promptGuard = promptGuard;
        this.rateLimitService = rateLimitService;
        this.chatLogMapper = chatLogMapper;
        this.properties = properties;
    }

    /**
     * 校验请求并创建绑定当前用户的 Agent。
     */
    public PreparedChat prepare(String sessionId, String message, Long userId, String clientKey) {
        String validationError = promptGuard.validate(message);
        if (validationError != null) {
            throw new AiChatException(validationError);
        }
        if (!rateLimitService.tryAcquire(clientKey)) {
            throw new AiChatException("请求太频繁，请稍后再试。");
        }
        String safeSessionId = normalizeSessionId(sessionId);
        ShoppingAssistant assistant = assistantFactory.create(userId);
        return new PreparedChat(safeSessionId, assistant, System.currentTimeMillis());
    }

    /**
     * 写审计日志。日志失败不影响主流程。
     */
    public void audit(Long userId, String sessionId, String question, String answer, long latencyMs, boolean success) {
        try {
            AiChatLog chatLog = new AiChatLog();
            chatLog.setUserId(userId);
            chatLog.setSessionId(sessionId);
            chatLog.setQuestion(truncate(question, QUESTION_LIMIT));
            chatLog.setAnswer(truncate(answer, ANSWER_LIMIT));
            chatLog.setLatencyMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, latencyMs)));
            chatLog.setSuccess(success ? 1 : 0);
            chatLogMapper.insert(chatLog);
        } catch (Exception e) {
            log.warn("Failed to persist AI chat audit log: {}", e.getMessage());
        }
    }

    public String friendlyError(Throwable error) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank() || "not-configured".equals(apiKey)) {
            return "AI 服务尚未配置，请设置环境变量 AI_API_KEY 后重启应用。";
        }
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        String lower = message.toLowerCase();
        if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("api key")) {
            return "AI 服务鉴权失败，请检查 AI_API_KEY 是否正确。";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "AI 服务响应超时，请稍后重试。";
        }
        return "AI 服务暂时不可用，请稍后重试。";
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId != null && sessionId.matches("[a-zA-Z0-9_-]{1,64}")) {
            return sessionId;
        }
        return UUID.randomUUID().toString();
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    public static class PreparedChat {
        private final String sessionId;
        private final ShoppingAssistant assistant;
        private final long startMillis;

        public PreparedChat(String sessionId, ShoppingAssistant assistant, long startMillis) {
            this.sessionId = sessionId;
            this.assistant = assistant;
            this.startMillis = startMillis;
        }

        public String getSessionId() {
            return sessionId;
        }

        public ShoppingAssistant getAssistant() {
            return assistant;
        }

        public long getStartMillis() {
            return startMillis;
        }
    }

    public static class AiChatException extends RuntimeException {
        public AiChatException(String message) {
            super(message);
        }
    }
}
