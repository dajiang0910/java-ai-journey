package com.example.notes_api.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EmbeddingServiceTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void embedShouldReturnNonEmptyVector() {
        float[] vector = embeddingService.embed("今天天气真好");

        assertThat(vector).isNotNull();
        assertThat(vector.length).isGreaterThan(0);
        // text-embedding-v2 应该是 1536 维
        assertThat(vector.length).isEqualTo(1536);
    }

    @Test
    void similarTextsShouldHaveHigherSimilarity() {
        // 语义相近的文本
        float[] catEatsFish = embeddingService.embed("猫吃鱼");
        float[] dogEatsMeat = embeddingService.embed("狗吃肉");
        // 语义无关的文本
        float[] weather = embeddingService.embed("今天天气很好，适合出去散步");

        double similarScore = EmbeddingService.cosineSimilarity(catEatsFish, dogEatsMeat);
        double unrelatedScore = EmbeddingService.cosineSimilarity(catEatsFish, weather);

        System.out.println("「猫吃鱼」vs「狗吃肉」相似度: " + similarScore);
        System.out.println("「猫吃鱼」vs「今天天气很好」相似度: " + unrelatedScore);

        // 语义相近的文本，相似度应该更高
        assertThat(similarScore).isGreaterThan(unrelatedScore);
        // 完全不相关的文本，相似度应该明显低于 0.8
        assertThat(unrelatedScore).isLessThan(0.8);
    }

    @Test
    void dimensionsShouldBeConsistent() {
        int dims = embeddingService.dimensions();
        assertThat(dims).isEqualTo(1536);
    }
}