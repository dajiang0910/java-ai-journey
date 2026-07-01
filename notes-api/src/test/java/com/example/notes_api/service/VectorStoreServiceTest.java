package com.example.notes_api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * VectorStoreService 纯单元测试。
 * <p>
 * 关键认知：测试的是 VectorStoreService 的业务逻辑（参数组装、调用委托、日志），
 * 不测试 VectorStore 本身（那是 Spring AI 的事）。
 * <p>
 * 用 Mockito mock VectorStore 接口——不需要 EmbeddingModel、不需要 Redis、不需要 Spring 容器。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VectorStoreService 单元测试")
class VectorStoreServiceTest {

    @Mock
    private VectorStore vectorStore;

    private VectorStoreService service;

    @BeforeEach
    void setUp() {
        service = new VectorStoreService(vectorStore);
    }

    @Nested
    @DisplayName("灌入文档")
    class Ingest {

        @Test
        @DisplayName("单文档灌入：应调用 vectorStore.add 且传入正确 content")
        void shouldCallAddWithDocument() {
            service.ingest("退款流程说明", Map.of("source", "help"));

            ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
            verify(vectorStore).add(captor.capture());

            List<Document> docs = captor.getValue();
            assertThat(docs).hasSize(1);
            assertThat(docs.get(0).getText()).isEqualTo("退款流程说明");
            assertThat(docs.get(0).getMetadata()).containsEntry("source", "help");
        }

        @Test
        @DisplayName("空 metadata：应正常工作")
        void shouldIngestWithEmptyMetadata() {
            service.ingest("内容", Map.of());

            verify(vectorStore).add(any());
        }
    }

    @Nested
    @DisplayName("语义检索")
    class Search {

        @Test
        @DisplayName("检索：应传入正确的 query、topK、threshold")
        void shouldBuildCorrectSearchRequest() {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of());

            service.search("如何退款", 5, 0.8);

            ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
            verify(vectorStore).similaritySearch(captor.capture());

            SearchRequest req = captor.getValue();
            assertThat(req.getQuery()).isEqualTo("如何退款");
            assertThat(req.getTopK()).isEqualTo(5);
            assertThat(req.getSimilarityThreshold()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("检索：默认参数 topK=4, threshold=0.7")
        void shouldUseDefaultParameters() {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of());

            service.search("测试查询");

            ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
            verify(vectorStore).similaritySearch(captor.capture());

            SearchRequest req = captor.getValue();
            assertThat(req.getTopK()).isEqualTo(4);
            assertThat(req.getSimilarityThreshold()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("检索：返回结果正确透传")
        void shouldReturnResults() {
            Document doc = new Document("匹配内容", Map.of("score", 0.95));
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(doc));

            List<Document> results = service.search("查询", 3, 0.5);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getText()).isEqualTo("匹配内容");
        }
    }

    @Nested
    @DisplayName("删除")
    class Delete {

        @Test
        @DisplayName("按 ID 列表删除：应委托给 vectorStore.delete")
        void shouldDelegateDelete() {
            service.delete(List.of("id-1", "id-2"));

            verify(vectorStore).delete(List.of("id-1", "id-2"));
        }
    }
}
