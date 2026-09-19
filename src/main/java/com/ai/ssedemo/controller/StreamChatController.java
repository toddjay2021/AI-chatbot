package com.ai.ssedemo.controller;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/stream-chat")
public class StreamChatController {

    // 会话空闲超过30分钟即被清理
    private static final long SESSION_IDLE_MILLIS = 30 * 60 * 1000L;

    // 会话条目：聊天记忆 + 最近访问时间戳
    private record SessionEntry(MessageWindowChatMemory memory, AtomicLong lastAccess) {}

    private final Map<String, SessionEntry> memoryMap = new ConcurrentHashMap<>();

    private final StreamingChatLanguageModel streamingChatModel;

    public StreamChatController(StreamingChatLanguageModel streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
    }

    // 每分钟扫描一次，清除空闲超时的会话，防止内存泄漏
    @Scheduled(fixedRate = 60_000L)
    public void evictExpiredSessions() {
        long now = System.currentTimeMillis();
        memoryMap.entrySet().removeIf(e -> now - e.getValue().lastAccess().get() > SESSION_IDLE_MILLIS);
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody StreamChatRequest req) {
        String sessionId = req.sessionId();
        String userInput = req.message();

        System.out.println("sessionId:" + sessionId + " userInput:" + userInput);

        // 取会话记忆，不存在则创建，并刷新最近访问时间
        SessionEntry entry = memoryMap.computeIfAbsent(sessionId,
                k -> new SessionEntry(MessageWindowChatMemory.withMaxMessages(8), new AtomicLong()));
        entry.lastAccess().set(System.currentTimeMillis());
        MessageWindowChatMemory chatMemory = entry.memory();

        UserMessage userMessage = UserMessage.userMessage(userInput);
        chatMemory.add(userMessage);
        List<ChatMessage> messages = chatMemory.messages();

        System.out.println("messages:" + messages.size());

        // timeout 为 0 表示永不超时，长文本生成不会被中断
        SseEmitter emitter = new SseEmitter(0L);

        streamingChatModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
            // 每收到一段文字
            @Override
            public void onNext(String partialResponse) {
                try {
                    emitter.send(SseEmitter.event().data(partialResponse));
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            // 全部生成完成
            @Override
            public void onComplete(Response<AiMessage> response) {
                AiMessage aiMessage = response.content();
                System.out.println(aiMessage.toString());
                chatMemory.add(aiMessage);
                emitter.complete();
            }

            // 发生错误
            @Override
            public void onError(Throwable error) {
                emitter.completeWithError(error);
            }
        });

        return emitter;
    }

    // 清空单个会话历史
    @PostMapping("/clear/{sessionId}")
    public void clearSession(@PathVariable String sessionId) {
        memoryMap.remove(sessionId);
    }
    // 请求实体
    public record StreamChatRequest(String sessionId, String message) {}
}
