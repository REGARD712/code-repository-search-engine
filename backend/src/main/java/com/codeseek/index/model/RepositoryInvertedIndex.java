package com.codeseek.index.model;

import java.util.Collections;
import java.util.Map;

/**
 * 内存中的仓库倒排索引。
 *
 * <p>文档列表使用数据库主键作为键；倒排表使用词项作为键。
 * 为了降低构建和加载时的复制开销，这里不复制底层 Map，调用方只应读取，不应修改。</p>
 */
public final class RepositoryInvertedIndex {

    private static final RepositoryInvertedIndex EMPTY = new RepositoryInvertedIndex(
            Collections.emptyMap(),
            Collections.emptyMap()
    );

    private final Map<Long, RepositoryDocument> documents;

    private final Map<String, PostingList> postings;

    /**
     * 创建倒排索引。
     *
     * @param documents 文档列表，key 为数据库主键
     * @param postings  倒排列表，key 为词项
     */
    public RepositoryInvertedIndex(
            Map<Long, RepositoryDocument> documents,
            Map<String, PostingList> postings
    ) {
        this.documents = documents;
        this.postings = postings;
    }

    /**
     * 创建空索引。
     *
     * @return 空索引
     */
    public static RepositoryInvertedIndex empty() {
        return EMPTY;
    }

    /**
     * 获取文档列表。
     *
     * @return 文档列表只读视图
     */
    public Map<Long, RepositoryDocument> documents() {
        return Collections.unmodifiableMap(documents);
    }

    /**
     * 获取倒排表。
     *
     * @return 倒排表只读视图
     */
    public Map<String, PostingList> postings() {
        return Collections.unmodifiableMap(postings);
    }

    /**
     * 获取文档数量。
     *
     * @return 文档数量
     */
    public int documentCount() {
        return documents.size();
    }

    /**
     * 获取词项数量。
     *
     * @return 词项数量
     */
    public int termCount() {
        return postings.size();
    }

    /**
     * 统计所有倒排项数量。
     *
     * @return 倒排项总数
     */
    public long postingCount() {
        long count = 0;
        for (PostingList postingList : postings.values()) {
            count += postingList.size();
        }
        return count;
    }
}
