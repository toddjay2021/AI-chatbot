package com.ai.chatdemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    //Example: http://localhost:8080/chat?msg=Hello
    @GetMapping("/chat")
    public String chat(@RequestParam String msg) {
        return chatService.chat(msg);
    }
}
