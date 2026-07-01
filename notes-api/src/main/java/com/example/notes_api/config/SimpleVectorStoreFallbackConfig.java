package com.example.notes_api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * SimpleVectorStore 降级配置。
 * <p>
 * 当 Redis Stack 不可用时（Docker 没装/起不来），设置 {@code spring.ai.vectorstore.mock=true} 作为逃生舱。
 * <p>
 * 降级二步操作：
 * <ol>
 *   <li>application.properties 加 {@code spring.ai.vectorstore.mock=true}</li>
 *   <li>application.properties 加
 *       {@code spring.autoconfigure.exclude=org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreAutoConfiguration}
 *       （防止 Redis 连接失败导致启动崩溃）</li>
 * </ol>
 * <p>
 * SimpleVectorStore = 内存向量库，所有数据存 ConcurrentHashMap。
 * 优点：零依赖，即开即用。缺点：重启数据丢失（v1 阶段够用）。
 * <p>
 * 关键认知：VectorStoreService 注入的是 VectorStore 接口，不关心谁实现——
 * 这就是"面向接口编程"让你在 Docker 炸了时还能继续开发的原因。
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.vectorstore.mock", havingValue = "true")
public class SimpleVectorStoreFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(SimpleVectorStoreFallbackConfig.class);

    @Bean
    @Primary
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        log.warn("⚠️ 启用 SimpleVectorStore（内存模式）—— 数据在重启后丢失，仅用于开发/测试");
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}

