package ltd.newbee.mall.ai.agent;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import ltd.newbee.mall.ai.config.AiProperties;
import ltd.newbee.mall.ai.rag.ProductRagService;
import ltd.newbee.mall.ai.tools.ShoppingTools;
import ltd.newbee.mall.dao.AiProductMapper;
import ltd.newbee.mall.service.NewBeeMallGoodsService;
import ltd.newbee.mall.service.NewBeeMallOrderService;
import org.springframework.stereotype.Component;

/**
 * 按请求创建 Agent 实例：把当前登录用户注入工具，实现数据隔离。
 */
@Component
public class ShoppingAssistantFactory {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final NewBeeMallGoodsService goodsService;
    private final NewBeeMallOrderService orderService;
    private final AiProductMapper productMapper;
    private final ProductRagService ragService;
    private final AiProperties properties;

    public ShoppingAssistantFactory(ChatModel chatModel,
                                    StreamingChatModel streamingChatModel,
                                    ChatMemoryProvider chatMemoryProvider,
                                    NewBeeMallGoodsService goodsService,
                                    NewBeeMallOrderService orderService,
                                    AiProductMapper productMapper,
                                    ProductRagService ragService,
                                    AiProperties properties) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.goodsService = goodsService;
        this.orderService = orderService;
        this.productMapper = productMapper;
        this.ragService = ragService;
        this.properties = properties;
    }

    public ShoppingAssistant create(Long userId) {
        ShoppingTools tools = new ShoppingTools(
                goodsService, orderService, productMapper, ragService, properties, userId);
        return AiServices.builder(ShoppingAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(tools)
                .maxToolCallingRoundTrips(4)
                .build();
    }
}
