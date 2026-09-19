package com.ai.ssedemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // 开启定时任务，用于清理过期会话
@SpringBootApplication
public class OllamaSseChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(OllamaSseChatApplication.class, args);
    }
}
