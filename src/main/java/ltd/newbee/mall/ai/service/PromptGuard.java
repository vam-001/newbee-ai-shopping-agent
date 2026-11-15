package ltd.newbee.mall.ai.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 输入安全校验：长度限制 + 常见提示词注入/危险指令拦截。
 */
@Component
public class PromptGuard {

    private static final int MAX_LENGTH = 500;

    private static final Pattern UNSAFE_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?(previous|above)\\s+instructions"
                    + "|忽略(之前|以上|前面|先前).{0,12}(指令|提示)"
                    + "|system\\s*prompt|系统提示词"
                    + "|drop\\s+table|truncate\\s+table|delete\\s+from"
                    + "|rm\\s+-rf|执行.{0,6}命令)");

    /**
     * @return null 表示通过；否则返回给用户的错误提示
     */
    public String validate(String message) {
        if (message == null || message.isBlank()) {
            return "消息不能为空。";
        }
        String trimmed = message.trim();
        if (trimmed.length() > MAX_LENGTH) {
            return "消息太长，请控制在 " + MAX_LENGTH + " 字以内。";
        }
        if (UNSAFE_PATTERN.matcher(trimmed).find()) {
            return "这个问题涉及不安全的操作，我不能执行。";
        }
        return null;
    }
}
