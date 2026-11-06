package com.codeseek.index.service;

import com.codeseek.index.builder.RepositoryIndexBuilder;
import com.codeseek.index.config.RepositoryIndexProperties;
import com.codeseek.index.model.RepositorySearchResult;
import com.codeseek.index.search.MultiWayIndexSearcher;
import com.codeseek.index.segment.JiebaTextSegmenter;
import com.codeseek.index.store.IndexFileStore;
import com.codeseek.repository.mapper.RepositoryMapper;
import com.codeseek.semantic.SemanticSearchException;
import com.codeseek.semantic.SemanticSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 混合检索逻辑测试。
 */
@ExtendWith(MockitoExtension.class)
class RepositoryIndexServiceImplTest {

    @Mock
    private RepositoryMapper repositoryMapper;

    @Mock
    private RepositoryIndexBuilder indexBuilder;

    @Mock
    private MultiWayIndexSearcher indexSearcher;

    @Mock
    private IndexFileStore indexFileStore;

    @Mock
    private JiebaTextSegmenter textSegmenter;

    @Mock
    private RepositoryIndexProperties properties;

    @Mock
    private SemanticSearchService semanticSearchService;

    @InjectMocks
    private RepositoryIndexServiceImpl repositoryIndexService;

    /**
     * 验证倒排结果与语义结果取交集，并保持语义结果顺序。
     */
    @Test
    void shouldIntersectAndKeepSemanticOrder() {
        RepositorySearchResult first = result(1L, "owner/one");
        RepositorySearchResult second = result(2L, "owner/two");
        RepositorySearchResult third = result(3L, "owner/three");

        when(textSegmenter.segment("大模型")).thenReturn(List.of("大模型"));
        when(indexSearcher.search(any(), anyList(), eq(1000)))
                .thenReturn(List.of(first, second, third));
        // 语义排序为 3、1、99，其中 99 不在倒排结果中，应被过滤。
        when(semanticSearchService.searchIds("大模型", 1000))
                .thenReturn(List.of(3L, 1L, 99L));

        List<RepositorySearchResult> results = repositoryIndexService.search("大模型", 10);

        assertThat(results)
                .extracting(RepositorySearchResult::id)
                .containsExactly(3L, 1L);
    }

    /**
     * 验证语义服务不可用时降级返回倒排结果。
     */
    @Test
    void shouldFallbackToInvertedResultsWhenSemanticServiceFails() {
        RepositorySearchResult first = result(1L, "owner/one");
        RepositorySearchResult second = result(2L, "owner/two");

        when(textSegmenter.segment("Spring")).thenReturn(List.of("spring"));
        when(indexSearcher.search(any(), anyList(), eq(1000)))
                .thenReturn(List.of(first, second));
        when(semanticSearchService.searchIds("Spring", 1000))
                .thenThrow(new SemanticSearchException("语义服务不可用", new RuntimeException("timeout")));

        List<RepositorySearchResult> results = repositoryIndexService.search("Spring", 10);

        assertThat(results)
                .extracting(RepositorySearchResult::id)
                .containsExactly(1L, 2L);
    }

    /**
     * 构造检索结果。
     *
     * @param id       仓库 ID
     * @param fullName 仓库全名
     * @return 检索结果
     */
    private RepositorySearchResult result(long id, String fullName) {
        return new RepositorySearchResult(
                id,
                fullName,
                "https://gitee.com/" + fullName,
                1,
                1
        );
    }
}
