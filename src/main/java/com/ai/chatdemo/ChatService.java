package com.ai.chatdemo;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatLanguageModel chatModel;

    // Spring自动注入Ollama模型
    public ChatService(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    public String chat(String userMessage) {
        return chatModel.generate(userMessage);
    }
}
