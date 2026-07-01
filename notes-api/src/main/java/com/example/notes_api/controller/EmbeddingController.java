package com.example.notes_api.controller;

import com.example.notes_api.dto.ApiResponse;
import com.example.notes_api.service.EmbeddingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/embed")
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * 比较两段文本的语义相似度
     */
    @PostMapping("/similarity")
    public ApiResponse<Map<String, Object>> similarity(
            @RequestParam String text1,
            @RequestParam String text2) {

        float[] vec1 = embeddingService.embed(text1);
        float[] vec2 = embeddingService.embed(text2);
        double similarity = EmbeddingService.cosineSimilarity(vec1, vec2);

        return ApiResponse.success(Map.of(
                "text1", text1,
                "text2", text2,
                "similarity", Math.round(similarity * 10000.0) / 10000.0, // 保留 4 位
                "dimensions", vec1.length
        ));
    }

    /**
     * 查看向量的前 5 个值（让人"看到"向量长什么样）
     */
    @PostMapping("/peek")
    public ApiResponse<Map<String, Object>> peek(@RequestParam String text) {
        float[] vector = embeddingService.embed(text);
        // 截取前 5 个值用于展示
        double[] head = new double[Math.min(5, vector.length)];
        for (int i = 0; i < head.length; i++) {
            head[i] = Math.round(vector[i] * 10000.0) / 10000.0;
        }
        return ApiResponse.success(Map.of(
                "text", text,
                "dimensions", vector.length,
                "head_values", head,
                "note", "完整向量有 " + vector.length + " 维，这里只展示前 5 个"
        ));
    }
}