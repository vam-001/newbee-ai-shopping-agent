package ltd.newbee.mall.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 导购模块配置。
 * 密钥通过环境变量 AI_API_KEY 注入，不写入仓库。
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** OpenAI 兼容接口的 API Key */
    private String apiKey;
    /** OpenAI 兼容接口地址，默认阿里云百炼 */
    private String baseUrl;
    /** 对话模型名称，默认 qwen-plus */
    private String chatModel;
    private Double temperature = 0.7;
    private Integer timeoutSeconds = 60;
    /** 每个会话保留的历史消息条数 */
    private Integer maxMemoryMessages = 20;
    /** RAG 召回条数 */
    private Integer ragTopK = 5;
    /** RAG 最低相似度 */
    private Double ragMinScore = 0.35;
    /** 每用户/每 IP 每分钟请求上限 */
    private Integer rateLimitPerMinute = 10;
    /** redis 或 in-memory */
    private String memoryStore = "redis";
    private Integer memoryTtlDays = 7;
    /** 向量索引批大小 */
    private Integer indexBatchSize = 50;
    /** 公网部署建议开启：要求登录后才能使用 AI 导购 */
    private Boolean requireLogin = false;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getMaxMemoryMessages() {
        return maxMemoryMessages;
    }

    public void setMaxMemoryMessages(Integer maxMemoryMessages) {
        this.maxMemoryMessages = maxMemoryMessages;
    }

    public Integer getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(Integer ragTopK) {
        this.ragTopK = ragTopK;
    }

    public Double getRagMinScore() {
        return ragMinScore;
    }

    public void setRagMinScore(Double ragMinScore) {
        this.ragMinScore = ragMinScore;
    }

    public Integer getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(Integer rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public String getMemoryStore() {
        return memoryStore;
    }

    public void setMemoryStore(String memoryStore) {
        this.memoryStore = memoryStore;
    }

    public Integer getMemoryTtlDays() {
        return memoryTtlDays;
    }

    public void setMemoryTtlDays(Integer memoryTtlDays) {
        this.memoryTtlDays = memoryTtlDays;
    }

    public Integer getIndexBatchSize() {
        return indexBatchSize;
    }

    public void setIndexBatchSize(Integer indexBatchSize) {
        this.indexBatchSize = indexBatchSize;
    }

    public Boolean getRequireLogin() {
        return requireLogin;
    }

    public void setRequireLogin(Boolean requireLogin) {
        this.requireLogin = requireLogin;
    }
}
