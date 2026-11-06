package com.codeseek.semantic;

import java.util.List;

/**
 * 仓库语义检索服务。
 *
 * <p>由 AI 向量模型根据查询词语义返回相关仓库 ID，结果按语义相似度降序排列。</p>
 */
public interface SemanticSearchService {

    /**
     * 执行语义检索。
     *
     * @param keyword 查询关键词
     * @param limit   最多返回条数
     * @return 按语义相似度降序排列的仓库 ID
     */
    List<Long> searchIds(String keyword, int limit);
}
