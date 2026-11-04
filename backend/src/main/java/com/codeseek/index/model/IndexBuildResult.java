package com.codeseek.index.model;

/**
 * 倒排索引构建结果。
 *
 * @param documentCount 文档数量
 * @param termCount     词项数量
 * @param postingCount 倒排项总数
 */
public record IndexBuildResult(int documentCount, int termCount, long postingCount) {
}
