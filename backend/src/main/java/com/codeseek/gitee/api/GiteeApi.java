package com.codeseek.gitee.api;

import com.codeseek.gitee.model.GiteeRepository;

import java.util.List;

/**
 * Gitee 搜索相关接口契约。
 *
 * <p>采用面向接口编程，业务层只依赖本接口，不直接依赖 HTTP 客户端实现。
 * 所有列表查询均会按 Gitee 分页参数自动翻页，直到取完当前条件下的全部数据。</p>
 */
public interface GiteeApi {

    /**
     * 搜索 Gitee 用户，返回用户名列表。
     *
     * <p>对应接口：{@code GET /v5/search/users}。Gitee 返回的用户对象字段较多，
     * 这里只取登录名（login），因为它可以直接作为后续用户仓库查询的入参。</p>
     *
     * @param keyword 搜索关键词，不能为空
     * @return 用户名列表，按 Gitee 返回顺序排列；无结果时返回空列表
     */
    List<String> searchUserNames(String keyword);

    /**
     * 查询某个用户的全部公开仓库。
     *
     * <p>对应接口：{@code GET /v5/users/{username}/repos}，会自动分页取全量。
     * 返回的仓库对象不包含 README 内容。</p>
     *
     * @param username 用户名（login），不能为空
     * @return 仓库列表；无结果时返回空列表
     */
    List<GiteeRepository> listUserRepositories(String username);

    /**
     * 查询某个组织的全部公开仓库。
     *
     * <p>对应接口：{@code GET /v5/orgs/{org}/repos}，会自动分页取全量。
     * 返回的仓库对象不包含 README 内容。</p>
     *
     * @param organization 组织名（path/login），不能为空
     * @return 仓库列表；无结果时返回空列表
     */
    List<GiteeRepository> listOrganizationRepositories(String organization);

    /**
     * 查询指定仓库的 README，并返回包含 README 的仓库列表。
     *
     * <p>对应接口：{@code GET /v5/repos/{owner}/{repo}/readme}。
     * Gitee 该接口一次只返回一个仓库的 README，因此按需求保持列表返回值，
     * 正常情况下列表只有一个元素；仓库没有 README 时返回空列表。</p>
     *
     * <p>README 接口本身不返回仓库 ID 和仓库网页地址，实现中会补查一次仓库详情接口，
     * 以保证返回对象的 {@code repositoryId} 和 {@code htmlUrl} 完整。</p>
     *
     * @param repositoryFullName 仓库全名，格式为 {@code owner/repo}，不能为空
     * @return 包含 README 的单元素仓库列表；没有 README 时返回空列表
     */
    List<GiteeRepository> getRepositoryReadme(String repositoryFullName);
}
