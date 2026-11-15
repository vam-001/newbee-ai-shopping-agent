package ltd.newbee.mall.ai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * AI 导购聊天页面。
 */
@Controller
public class AiChatPageController {

    @GetMapping("/ai/chat")
    public String chatPage() {
        return "mall/ai-chat";
    }
}
