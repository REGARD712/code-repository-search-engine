package com.codeseek.index;

import com.codeseek.index.model.IndexBuildResult;
import com.codeseek.index.model.RepositoryInvertedIndex;
import com.codeseek.index.model.RepositorySearchResult;
import com.codeseek.index.service.RepositoryIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 测试 {@link RepositoryIndexService} 类。
 *
 * @ClassName: RepositoryIndexServiceTest
 * @Author: JacksonLee
 * @Date: 2026/11/5
 */
@SpringBootTest
public class RepositoryIndexServiceTest {

    @Autowired
    private RepositoryIndexService repositoryIndexService;

    /**
     * 验证倒排索引构建与分词。
     */
    @Test
    void shouldBuildIndexAndSearchBySegmentedTerms() {
        IndexBuildResult index = repositoryIndexService.rebuildIndex();

        System.out.println("documentCount: " + index.documentCount());
        System.out.println("termCount: " + index.termCount());
        System.out.println("postingCount: " + index.postingCount());
    }

    /**
     * 验证按关键词搜索仓库。
     */
    @Test
    void shouldMergePostingListsAndSortByFrequency() {
        RepositoryInvertedIndex index = repositoryIndexService.loadIndex();

        List<RepositorySearchResult> results = repositoryIndexService.search("大模型", 10);

        results.forEach(System.out::println);
    }
}
