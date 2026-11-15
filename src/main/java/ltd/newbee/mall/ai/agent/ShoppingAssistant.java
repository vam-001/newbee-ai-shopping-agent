package ltd.newbee.mall.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * AI 导购 Agent 的声明式接口。
 * 系统提示词放在 resources/prompts/shopping-agent-system.txt，方便迭代。
 */
public interface ShoppingAssistant {

    @SystemMessage(fromResource = "prompts/shopping-agent-system.txt")
    String chat(@MemoryId String sessionId, @UserMessage String message);

    @SystemMessage(fromResource = "prompts/shopping-agent-system.txt")
    TokenStream chatStream(@MemoryId String sessionId, @UserMessage String message);
}
