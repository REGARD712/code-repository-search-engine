package com.codeseek.index.model;

/**
 * 倒排索引中的文档信息。
 *
 * <p>只保存搜索时需要返回的仓库基础字段，不重复保存 README，降低内存占用。</p>
 *
 * @param id      数据库仓库主键，也是倒排索引中的文档 ID
 * @param fullName 仓库全名
 * @param htmlUrl 仓库网页地址
 */
public record RepositoryDocument(long id, String fullName, String htmlUrl) {
}
