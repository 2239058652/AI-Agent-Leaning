package com.assistant.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "API_MODEL_KEY", matches = ".+")
class ZhiPuEmbeddingTest {

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * (double) b[i];
            normA += a[i] * (double) a[i];
            normB += b[i] * (double) b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Test
    void similarSentencesAreCloserThanUnrelated() {
        ZhiPuAiApi api = ZhiPuAiApi.builder()
                .apiKey(System.getenv("API_MODEL_KEY"))
                .build();
        ZhiPuAiEmbeddingModel model = new ZhiPuAiEmbeddingModel(api, MetadataMode.NONE);

        List<float[]> vectors = model.embed(List.of(
                "只能取消自己的订单",
                "用户只能取消本人的订单",
                "明天北京下雨吗"));

        assertEquals(3, vectors.size());
        int dimensions = vectors.get(0).length;
        assertTrue(dimensions > 8);
        assertEquals(dimensions, vectors.get(1).length);
        assertEquals(dimensions, vectors.get(2).length);

        double sameMeaning = cosine(vectors.get(0), vectors.get(1));
        double otherMeaning = cosine(vectors.get(0), vectors.get(2));
        assertTrue(sameMeaning > otherMeaning,
                "同义 " + sameMeaning + " 应大于无关 " + otherMeaning);
    }
}