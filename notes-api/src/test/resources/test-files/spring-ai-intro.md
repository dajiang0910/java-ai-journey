# Spring AI 框架入门指南

## 什么是 Spring AI

Spring AI 是 Spring 生态的 AI 集成框架。它将大模型调用封装为普通的 Spring Bean，
让 Java 开发者零门槛上手 LLM 应用开发。

## 核心组件

- **ChatClient**: 统一的对话客户端，支持同步/流式调用
- **EmbeddingModel**: 文本向量化接口
- **VectorStore**: 向量数据库抽象层
- **BeanOutputConverter**: 结构化输出转换器

## 快速开始

```java
@RestController
public class ChatController {
    private final ChatClient chatClient;

    public String chat(@RequestParam String message) {
        return chatClient.prompt()
            .user(message)
            .call()
            .content();
    }
}
```

Spring AI 让 Java 开发者无需学习 Python 即可构建 AI 应用。
