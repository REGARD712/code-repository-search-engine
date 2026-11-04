package com.codeseek.index.builder;

import com.codeseek.index.model.PostingList;
import com.codeseek.index.model.RepositoryDocument;
import com.codeseek.index.model.RepositoryInvertedIndex;
import com.codeseek.index.segment.JiebaTextSegmenter;
import com.codeseek.repository.entity.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仓库倒排索引构建器。
 *
 * <p>从 repository 表数据构建：</p>
 * <ul>
 *     <li>以数据库主键作为文档 ID，建立文档列表；</li>
 *     <li>清洗 README 特殊字符后使用结巴搜索引擎模式分词；</li>
 *     <li>统计每个词在每个文档中的词频，形成倒排列表。</li>
 * </ul>
 */
@Component
public class RepositoryIndexBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryIndexBuilder.class);

    private final JiebaTextSegmenter textSegmenter;

    /**
     * 创建索引构建器。
     *
     * @param textSegmenter 结巴分词器
     */
    public RepositoryIndexBuilder(JiebaTextSegmenter textSegmenter) {
        this.textSegmenter = textSegmenter;
    }

    /**
     * 构建倒排索引。
     *
     * @param repositories 仓库数据
     * @return 内存倒排索引
     */
    public RepositoryInvertedIndex build(List<Repository> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return RepositoryInvertedIndex.empty();
        }

        Map<Long, RepositoryDocument> documents = new HashMap<>(capacity(repositories.size()));
        // 第一层 key 是词项，第二层 key 是文档 ID，value 是词频。
        Map<String, Map<Long, Integer>> termDocumentFrequencies = new HashMap<>();

        for (Repository repository : repositories) {
            if (repository == null || repository.getId() == null) {
                continue;
            }

            long documentId = repository.getId();
            documents.put(documentId, new RepositoryDocument(
                    documentId,
                    repository.getFullName(),
                    repository.getHtmlUrl()
            ));

            List<String> words = textSegmenter.segment(repository.getReadme());
            if (words.isEmpty()) {
                continue;
            }

            // 先统计当前文档内部的词频，再把词频写入对应词的倒排表。
            Map<String, Integer> localFrequencies = new HashMap<>(capacity(words.size()));
            for (String word : words) {
                localFrequencies.merge(word, 1, Integer::sum);
            }

            for (Map.Entry<String, Integer> entry : localFrequencies.entrySet()) {
                termDocumentFrequencies
                        .computeIfAbsent(entry.getKey(), key -> new HashMap<>())
                        .merge(documentId, entry.getValue(), Integer::sum);
            }
        }

        Map<String, PostingList> postings = new HashMap<>(capacity(termDocumentFrequencies.size()));
        for (Map.Entry<String, Map<Long, Integer>> entry : termDocumentFrequencies.entrySet()) {
            Map<Long, Integer> documentFrequencies = entry.getValue();

            // 文档 ID 排序后写入原始数组，保证后续多路归并可以顺序推进。
            long[] documentIds = documentFrequencies.keySet().stream()
                    .mapToLong(Long::longValue)
                    .sorted()
                    .toArray();
            int[] frequencies = new int[documentIds.length];
            for (int i = 0; i < documentIds.length; i++) {
                frequencies[i] = documentFrequencies.get(documentIds[i]);
            }

            postings.put(entry.getKey(), new PostingList(documentIds, frequencies));
        }

        RepositoryInvertedIndex index = new RepositoryInvertedIndex(documents, postings);
        LOGGER.info("倒排索引构建完成：文档数={}，词项数={}，倒排项数={}",
                index.documentCount(), index.termCount(), index.postingCount());
        return index;
    }

    /**
     * 计算 HashMap 初始容量，减少扩容和重新哈希。
     *
     * @param expectedSize 预估元素数量
     * @return 初始容量
     */
    private int capacity(int expectedSize) {
        if (expectedSize < 1) {
            return 1;
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) (expectedSize / 0.75f) + 1);
    }
}
