package com.ai.ssedemo.controller;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/stream-chat")
public class StreamChatController {

    private final StreamingChatLanguageModel streamingChatModel;
    private final Map<String, MessageWindowChatMemory> memoryMap = new ConcurrentHashMap<>();

    public StreamChatController(StreamingChatLanguageModel streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody StreamChatRequest req) {
        String sessionId = req.sessionId();
        String userInput = req.message();

        System.out.println("sessionId:" + sessionId + " userInput:" + userInput);

        MessageWindowChatMemory chatMemory = memoryMap.computeIfAbsent(sessionId,
                k -> MessageWindowChatMemory.withMaxMessages(8));

        UserMessage userMessage = UserMessage.userMessage(userInput);
        chatMemory.add(userMessage);
        List<ChatMessage> messages = chatMemory.messages();

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
