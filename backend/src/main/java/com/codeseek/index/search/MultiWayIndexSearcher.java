package com.codeseek.index.search;

import com.codeseek.index.model.PostingList;
import com.codeseek.index.model.RepositoryDocument;
import com.codeseek.index.model.RepositoryInvertedIndex;
import com.codeseek.index.model.RepositorySearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 基于倒排索引的多路归并检索器。
 *
 * <p>每个查询词对应一路有序倒排列表。使用小根堆按文档 ID 归并所有倒排列表，
 * 同一个文档 ID 会一次性合并，累加查询词在该文档中的词频；
 * 归并完成后按词频降序、命中词数降序、文档 ID 升序返回结果。</p>
 */
@Component
public class MultiWayIndexSearcher {

    /**
     * 默认返回条数。
     */
    private static final int DEFAULT_LIMIT = 20;

    /**
     * 单次检索最大返回条数。
     */
    private static final int MAX_LIMIT = 1000;

    /**
     * 执行多路归并检索。
     *
     * @param index      倒排索引
     * @param queryTerms 查询词列表，必须已经完成搜索引擎模式分词
     * @param limit      返回条数，小于等于 0 时使用默认值
     * @return 按词频排序的仓库搜索结果
     */
    public List<RepositorySearchResult> search(
            RepositoryInvertedIndex index,
            List<String> queryTerms,
            int limit
    ) {
        if (index == null || queryTerms == null || queryTerms.isEmpty()) {
            return List.of();
        }

        // 查询词去重，避免重复词导致同一倒排列表被重复加入归并。
        Set<String> distinctTerms = new LinkedHashSet<>();
        for (String term : queryTerms) {
            if (term != null && !term.isBlank()) {
                distinctTerms.add(term);
            }
        }

        PriorityQueue<PostingCursor> queue = new PriorityQueue<>(
                Math.max(1, distinctTerms.size()),
                Comparator.comparingLong(PostingCursor::currentDocumentId)
        );

        for (String term : distinctTerms) {
            PostingList postingList = index.postings().get(term);
            if (postingList != null && postingList.size() > 0) {
                queue.add(new PostingCursor(postingList));
            }
        }

        if (queue.isEmpty()) {
            return List.of();
        }

        List<ScoredDocument> scoredDocuments = new ArrayList<>();

        // 多路归并：每次取出当前最小文档 ID，并把所有相同文档 ID 的词频合并。
        while (!queue.isEmpty()) {
            PostingCursor cursor = queue.poll();
            long documentId = cursor.currentDocumentId();
            long frequency = cursor.currentFrequency();
            int matchedTermCount = 1;

            cursor.advance();
            if (cursor.hasNext()) {
                queue.add(cursor);
            }

            while (!queue.isEmpty() && queue.peek().currentDocumentId() == documentId) {
                PostingCursor sameDocumentCursor = queue.poll();
                frequency += sameDocumentCursor.currentFrequency();
                matchedTermCount++;

                sameDocumentCursor.advance();
                if (sameDocumentCursor.hasNext()) {
                    queue.add(sameDocumentCursor);
                }
            }

            RepositoryDocument document = index.documents().get(documentId);
            if (document != null) {
                scoredDocuments.add(new ScoredDocument(document, frequency, matchedTermCount));
            }
        }

        // 归并只保证文档 ID 有序，最终结果按词频排序。
        scoredDocuments.sort(Comparator
                .comparingLong(ScoredDocument::frequency)
                .reversed()
                .thenComparing(Comparator.comparingInt(ScoredDocument::matchedTermCount).reversed())
                .thenComparingLong(item -> item.document().id()));

        int safeLimit = resolveLimit(limit);
        return scoredDocuments.stream()
                .limit(safeLimit)
                .map(item -> new RepositorySearchResult(
                        item.document().id(),
                        item.document().fullName(),
                        item.document().htmlUrl(),
                        item.frequency(),
                        item.matchedTermCount()
                ))
                .toList();
    }

    /**
     * 处理返回条数。
     *
     * @param limit 原始返回条数
     * @return 合法返回条数
     */
    private int resolveLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 单路倒排列表游标。
     */
    private static final class PostingCursor {

        private final PostingList postingList;

        private int index;

        /**
         * 创建游标。
         *
         * @param postingList 倒排列表
         */
        private PostingCursor(PostingList postingList) {
            this.postingList = postingList;
            this.index = 0;
        }

        /**
         * 当前文档 ID。
         *
         * @return 文档 ID
         */
        private long currentDocumentId() {
            return postingList.documentIdAt(index);
        }

        /**
         * 当前文档词频。
         *
         * @return 词频
         */
        private int currentFrequency() {
            return postingList.frequencyAt(index);
        }

        /**
         * 是否还有下一个倒排项。
         *
         * @return true 表示还有
         */
        private boolean hasNext() {
            return index < postingList.size();
        }

        /**
         * 移动到下一个倒排项。
         */
        private void advance() {
            index++;
        }
    }

    /**
     * 归并过程中的临时打分文档。
     *
     * @param document          文档信息
     * @param frequency          词频总和
     * @param matchedTermCount   命中词数
     */
    private record ScoredDocument(
            RepositoryDocument document,
            long frequency,
            int matchedTermCount
    ) {
    }
}
