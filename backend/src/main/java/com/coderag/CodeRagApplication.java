package com.coderag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * CodeRAG 多平台代码智能学习平台 - 启动类
 */
@SpringBootApplication
@EnableAsync
public class CodeRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeRagApplication.class, args);
    }
}
