package com.example.notes_api.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 将文本转为向量
     * @return float[] 1536 维向量（text-embedding-v2）
     */
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    /**
     * 批量 Embedding（灌库时会用到）
     */
    public List<float[]> embedBatch(List<String> texts) {
        return embeddingModel.embed(texts);
    }

    /**
     * 获取 Embedding 维度（用于校验）
     */
    public int dimensions() {
        return embeddingModel.dimensions();
    }

    /**
     * 计算两个向量的余弦相似度
     * cos(θ) = (A·B) / (|A| × |B|)
     * 返回值范围 [-1, 1]，越接近 1 越相似
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致: " + a.length + " vs " + b.length);
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0; // 零向量 → 无意义，返回 0
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}