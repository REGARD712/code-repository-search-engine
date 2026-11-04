package com.codeseek.index.model;

/**
 * 仓库倒排索引搜索结果。
 *
 * @param id                数据库仓库主键
 * @param fullName          仓库全名
 * @param htmlUrl           仓库网页地址
 * @param frequency         查询词在文档中的词频总和
 * @param matchedTermCount  命中的查询词数量
 */
public record RepositorySearchResult(
        long id,
        String fullName,
        String htmlUrl,
        long frequency,
        int matchedTermCount
) {
}
