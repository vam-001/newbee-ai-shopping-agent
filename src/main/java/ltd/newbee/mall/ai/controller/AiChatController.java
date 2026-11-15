package ltd.newbee.mall.ai.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import ltd.newbee.mall.ai.config.AiProperties;
import ltd.newbee.mall.ai.rag.ProductRagService;
import ltd.newbee.mall.ai.service.AiChatService;
import ltd.newbee.mall.common.Constants;
import ltd.newbee.mall.controller.vo.NewBeeMallUserVO;
import ltd.newbee.mall.util.Result;
import ltd.newbee.mall.util.ResultGenerator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 导购 HTTP 接口。
 * /ai/chat       同步问答
 * /ai/chat/stream SSE 流式问答
 * /ai/status     索引与模型状态
 */
@RestController
@RequestMapping("/ai")
public class AiChatController {

    private static final long SSE_TIMEOUT_MILLIS = 180_000L;

    private final AiChatService aiChatService;
    private final ProductRagService productRagService;
    private final AiProperties aiProperties;

    public AiChatController(AiChatService aiChatService,
                            ProductRagService productRagService,
                            AiProperties aiProperties) {
        this.aiChatService = aiChatService;
        this.productRagService = productRagService;
        this.aiProperties = aiProperties;
    }

    @PostMapping("/chat")
    public Result chat(@RequestBody ChatRequest request, HttpSession session, HttpServletRequest httpRequest) {
        Long userId = currentUserId(session);
        String clientKey = clientKey(userId, httpRequest);
        AiChatService.PreparedChat prepared;
        try {
            prepared = aiChatService.prepare(request.getSessionId(), request.getMessage(), userId, clientKey);
        } catch (AiChatService.AiChatException e) {
            return ResultGenerator.genFailResult(e.getMessage());
        }

        long start = prepared.getStartMillis();
        try {
            String answer = prepared.getAssistant().chat(prepared.getSessionId(), request.getMessage());
            aiChatService.audit(userId, prepared.getSessionId(), request.getMessage(), answer,
                    System.currentTimeMillis() - start, true);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId", prepared.getSessionId());
            data.put("answer", answer);
            return ResultGenerator.genSuccessResult(data);
        } catch (Exception e) {
            aiChatService.audit(userId, prepared.getSessionId(), request.getMessage(), "",
                    System.currentTimeMillis() - start, false);
            return ResultGenerator.genFailResult(aiChatService.friendlyError(e));
        }
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("message") String message,
                             @RequestParam(value = "sessionId", required = false) String sessionId,
                             HttpSession session,
                             HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        Long userId = currentUserId(session);
        String clientKey = clientKey(userId, httpRequest);

        AiChatService.PreparedChat prepared;
        try {
            prepared = aiChatService.prepare(sessionId, message, userId, clientKey);
        } catch (AiChatService.AiChatException e) {
            sendEvent(emitter, "error", e.getMessage());
            emitter.complete();
            return emitter;
        }

        String effectiveSessionId = prepared.getSessionId();
        StringBuilder answer = new StringBuilder();
        AtomicBoolean finished = new AtomicBoolean(false);
        long start = prepared.getStartMillis();

        try {
            prepared.getAssistant().chatStream(effectiveSessionId, message)
                    .onPartialResponse(token -> {
                        answer.append(token);
                        sendEvent(emitter, "message", token);
                    })
                    .onToolExecuted(execution ->
                            sendEvent(emitter, "tool", execution.request().name()))
                    .onCompleteResponse(response -> {
                        if (finished.compareAndSet(false, true)) {
                            aiChatService.audit(userId, effectiveSessionId, message, answer.toString(),
                                    System.currentTimeMillis() - start, true);
                            sendEvent(emitter, "done", effectiveSessionId);
                            emitter.complete();
                        }
                    })
                    .onError(error -> {
                        if (finished.compareAndSet(false, true)) {
                            aiChatService.audit(userId, effectiveSessionId, message, answer.toString(),
                                    System.currentTimeMillis() - start, false);
                            sendEvent(emitter, "error", aiChatService.friendlyError(error));
                            emitter.complete();
                        }
                    })
                    .start();
        } catch (Exception e) {
            if (finished.compareAndSet(false, true)) {
                aiChatService.audit(userId, effectiveSessionId, message, answer.toString(),
                        System.currentTimeMillis() - start, false);
                sendEvent(emitter, "error", aiChatService.friendlyError(e));
                emitter.complete();
            }
        }
        return emitter;
    }

    @GetMapping("/status")
    public Result status() {
        boolean apiConfigured = aiProperties.getApiKey() != null
                && !aiProperties.getApiKey().isBlank()
                && !"not-configured".equals(aiProperties.getApiKey());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apiConfigured", apiConfigured);
        data.put("chatModel", aiProperties.getChatModel());
        data.put("ragReady", productRagService.isReady());
        data.put("indexedProducts", productRagService.getIndexedCount());
        data.put("indexBuildMillis", productRagService.getLastBuildMillis());
        data.put("memoryStore", aiProperties.getMemoryStore());
        return ResultGenerator.genSuccessResult(data);
    }

    private Long currentUserId(HttpSession session) {
        Object user = session.getAttribute(Constants.MALL_USER_SESSION_KEY);
        if (user instanceof NewBeeMallUserVO) {
            return ((NewBeeMallUserVO) user).getUserId();
        }
        return null;
    }

    private String clientKey(Long userId, HttpServletRequest request) {
        return userId != null ? "u:" + userId : "ip:" + request.getRemoteAddr();
    }

    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data == null ? "" : data));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    public static class ChatRequest {
        private String message;
        private String sessionId;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }
}
