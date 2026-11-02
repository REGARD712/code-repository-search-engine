package com.codeseek.gitee.client;

import com.codeseek.gitee.api.GiteeApi;
import com.codeseek.gitee.config.GiteeApiProperties;
import com.codeseek.gitee.exception.GiteeApiException;
import com.codeseek.gitee.model.GiteeRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 基于 Spring RestClient 的 Gitee API 实现。
 *
 * <p>类中统一处理分页、访问令牌、错误转换和字段映射。
 * 所有对外方法都会自动翻页获取全量数据，调用方不需要关心 page/per_page 参数。</p>
 */
public class GiteeApiClient implements GiteeApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(GiteeApiClient.class);

    /**
     * Gitee 单页最大条数。
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final RestClient restClient;

    private final GiteeApiProperties properties;

    /**
     * 创建 Gitee API 客户端。
     *
     * @param restClient RestClient 实例
     * @param properties Gitee API 配置
     */
    public GiteeApiClient(RestClient restClient, GiteeApiProperties properties) {
        this.restClient = Objects.requireNonNull(restClient, "restClient 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> searchUserNames(String keyword) {
        String normalizedKeyword = requireText(keyword, "搜索关键词");

        List<GiteeUserResponse> users = fetchAllPages(
                (page, perPage) -> fetchUserPage(normalizedKeyword, page, perPage),
                "搜索 Gitee 用户"
        );

        // 只保留登录名，并去重；登录名可直接用于后续用户仓库查询。
        return users.stream()
                .map(GiteeUserResponse::login)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GiteeRepository> listUserRepositories(String username) {
        String normalizedUsername = requireText(username, "用户名");

        List<GiteeRepoResponse> repositories = fetchAllPages(
                (page, perPage) -> fetchUserRepoPage(normalizedUsername, page, perPage),
                "查询用户仓库"
        );

        return toRepositories(repositories);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GiteeRepository> listOrganizationRepositories(String organization) {
        String normalizedOrganization = requireText(organization, "组织名");

        List<GiteeRepoResponse> repositories = fetchAllPages(
                (page, perPage) -> fetchOrganizationRepoPage(normalizedOrganization, page, perPage),
                "查询组织仓库"
        );

        return toRepositories(repositories);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GiteeRepository> getRepositoryReadme(String repositoryFullName) {
        RepoName repoName = parseRepositoryFullName(repositoryFullName);

        GiteeReadmeResponse readme;
        try {
            readme = fetchReadme(repoName.owner(), repoName.repo());
        } catch (GiteeApiException exception) {
            // 仓库没有 README 时 Gitee 返回 404，这种情况按空列表处理，不阻断批量处理。
            if (exception.getStatusCode() == 404) {
                return List.of();
            }
            throw exception;
        }

        if (readme == null) {
            return List.of();
        }

        // README 响应不包含仓库 ID 和仓库网页地址，补查仓库详情保证字段完整。
        GiteeRepoDetailResponse detail = fetchRepositoryDetail(repoName.owner(), repoName.repo());
        if (detail == null) {
            throw new GiteeApiException("查询仓库详情返回空响应：" + repositoryFullName);
        }

        String readmeContent = decodeReadme(readme);
        String fullName = StringUtils.hasText(detail.fullName())
                ? detail.fullName()
                : repositoryFullName.trim();

        return List.of(new GiteeRepository(
                fullName,
                detail.htmlUrl(),
                readmeContent,
                detail.id()
        ));
    }

    /**
     * 分页获取全量数据。
     *
     * <p>Gitee 的分页接口不保证一定有总数，因此采用“当前页条数小于 perPage 即结束”的策略。
     * 同时设置最大页数保护，防止接口异常时无限循环。</p>
     *
     * @param pageFetcher 单页查询函数
     * @param operation   操作名称，用于异常信息
     * @param <T>         数据类型
     * @return 全量数据
     */
    private <T> List<T> fetchAllPages(PageFetcher<T> pageFetcher, String operation) {
        List<T> all = new ArrayList<>();
        int page = 1;
        int perPage = resolvePageSize();
        int maxPages = resolveMaxPages();

        while (true) {
            if (page > maxPages) {
                throw new GiteeApiException(
                        operation + " 超过最大分页数 " + maxPages + "，已停止继续查询，请检查数据量或调整 gitee.api.max-pages"
                );
            }

            List<T> batch = pageFetcher.fetch(page, perPage);
            if (batch == null || batch.isEmpty()) {
                break;
            }

            all.addAll(batch);

            // Gitee 返回不足一页，说明已经到最后一页。
            if (batch.size() < perPage) {
                break;
            }

            page++;
        }

        LOGGER.debug("{} 完成，共获取 {} 条数据，分页数 {}", operation, all.size(), page);
        return all;
    }

    /**
     * 查询一页用户数据。
     *
     * @param keyword 搜索关键词
     * @param page    页码
     * @param perPage 每页条数
     * @return 当前页用户
     */
    private List<GiteeUserResponse> fetchUserPage(String keyword, int page, int perPage) {
        return execute("搜索 Gitee 用户", () -> restClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder.path("/search/users")
                            .queryParam("q", keyword)
                            .queryParam("page", page)
                            .queryParam("per_page", perPage);
                    appendAccessToken(builder);
                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<GiteeUserResponse>>() {
                }));
    }

    /**
     * 查询一页用户仓库数据。
     *
     * @param username 用户名
     * @param page     页码
     * @param perPage  每页条数
     * @return 当前页仓库
     */
    private List<GiteeRepoResponse> fetchUserRepoPage(String username, int page, int perPage) {
        return execute("查询用户仓库", () -> restClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder.path("/users/{username}/repos")
                            .queryParam("type", "all")
                            // 固定排序，避免翻页过程中数据顺序变化导致漏取或重复。
                            .queryParam("sort", "full_name")
                            .queryParam("direction", "asc")
                            .queryParam("page", page)
                            .queryParam("per_page", perPage);
                    appendAccessToken(builder);
                    return builder.build(username);
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<GiteeRepoResponse>>() {
                }));
    }

    /**
     * 查询一页组织仓库数据。
     *
     * @param organization 组织名
     * @param page         页码
     * @param perPage      每页条数
     * @return 当前页仓库
     */
    private List<GiteeRepoResponse> fetchOrganizationRepoPage(String organization, int page, int perPage) {
        return execute("查询组织仓库", () -> restClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder.path("/orgs/{org}/repos")
                            // 搜索场景只获取公开仓库，避免把私有仓库带入索引。
                            .queryParam("type", "public")
                            .queryParam("page", page)
                            .queryParam("per_page", perPage);
                    appendAccessToken(builder);
                    return builder.build(organization);
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<GiteeRepoResponse>>() {
                }));
    }

    /**
     * 查询仓库 README。
     *
     * @param owner 仓库所有者
     * @param repo  仓库名
     * @return README 响应
     */
    private GiteeReadmeResponse fetchReadme(String owner, String repo) {
        return execute("获取仓库 README", () -> restClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder.path("/repos/{owner}/{repo}/readme");
                    appendAccessToken(builder);
                    return builder.build(owner, repo);
                })
                .retrieve()
                .body(GiteeReadmeResponse.class));
    }

    /**
     * 查询仓库详情，用于补全仓库 ID 和网页地址。
     *
     * @param owner 仓库所有者
     * @param repo  仓库名
     * @return 仓库详情
     */
    private GiteeRepoDetailResponse fetchRepositoryDetail(String owner, String repo) {
        return execute("查询仓库详情", () -> restClient.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder.path("/repos/{owner}/{repo}");
                    appendAccessToken(builder);
                    return builder.build(owner, repo);
                })
                .retrieve()
                .body(GiteeRepoDetailResponse.class));
    }

    /**
     * 执行 HTTP 请求并统一转换异常。
     *
     * @param operation 操作名称
     * @param request   请求逻辑
     * @param <T>       响应类型
     * @return 响应结果
     */
    private <T> T execute(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            throw new GiteeApiException(
                    operation + " 失败，HTTP 状态码：" + exception.getStatusCode().value(),
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new GiteeApiException(operation + " 失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 追加 access_token 查询参数。
     *
     * @param builder URI 构建器
     */
    private void appendAccessToken(UriBuilder builder) {
        if (StringUtils.hasText(properties.getAccessToken())) {
            builder.queryParam("access_token", properties.getAccessToken());
        }
    }

    /**
     * 将 Gitee 仓库响应批量转换为统一模型，并按仓库全名去重。
     *
     * <p>分页期间仓库数据可能发生变化，理论上存在跨页重复的可能；以仓库全名为键去重，
     * 避免同一仓库在结果中出现多次。</p>
     *
     * @param responses Gitee 仓库响应列表
     * @return 去重后的仓库列表
     */
    private List<GiteeRepository> toRepositories(List<GiteeRepoResponse> responses) {
        Map<String, GiteeRepository> uniqueRepositories = new LinkedHashMap<>();
        for (GiteeRepoResponse response : responses) {
            GiteeRepository repository = toRepository(response);
            if (repository != null) {
                uniqueRepositories.putIfAbsent(repository.fullName(), repository);
            }
        }
        return List.copyOf(uniqueRepositories.values());
    }

    /**
     * 将 Gitee 仓库响应转换为统一模型。
     *
     * @param response Gitee 仓库响应
     * @return 仓库模型，字段不足时返回 null
     */
    private GiteeRepository toRepository(GiteeRepoResponse response) {
        if (response == null || !StringUtils.hasText(response.fullName())) {
            return null;
        }
        // 即使使用了带私有仓库权限的 Token，也不把私有仓库带入搜索数据。
        if (Boolean.TRUE.equals(response.privateRepository())) {
            return null;
        }
        return new GiteeRepository(
                response.fullName(),
                response.htmlUrl(),
                null,
                response.id()
        );
    }

    /**
     * 将 README 内容解码为文本。
     *
     * <p>Gitee 默认返回 base64 编码，且内容中可能包含换行，因此使用 MIME 解码器。
     * 若 encoding 不是 base64，则按原文返回，兼容接口未来可能的调整。</p>
     *
     * @param readme README 响应
     * @return 解码后的 README 文本
     */
    private String decodeReadme(GiteeReadmeResponse readme) {
        String content = readme.content();
        if (!StringUtils.hasText(content)) {
            return "";
        }

        if (StringUtils.hasText(readme.encoding())
                && !"base64".equalsIgnoreCase(readme.encoding())) {
            return content;
        }

        try {
            byte[] decoded = Base64.getMimeDecoder().decode(content);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new GiteeApiException("README Base64 解码失败", exception);
        }
    }

    /**
     * 解析 owner/repo 格式的仓库全名。
     *
     * @param repositoryFullName 仓库全名
     * @return 解析后的 owner 和 repo
     */
    private RepoName parseRepositoryFullName(String repositoryFullName) {
        String normalized = requireText(repositoryFullName, "仓库全名");
        String[] parts = normalized.split("/", 2);
        if (parts.length != 2
                || !StringUtils.hasText(parts[0])
                || !StringUtils.hasText(parts[1])) {
            throw new IllegalArgumentException("仓库全名必须为 owner/repo 格式：" + repositoryFullName);
        }
        return new RepoName(parts[0].trim(), parts[1].trim());
    }

    /**
     * 校验字符串非空并去除首尾空白。
     *
     * @param value     待校验字符串
     * @param fieldName 字段名称
     * @return 去空白后的字符串
     */
    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    /**
     * 计算实际使用的每页条数。
     *
     * @return 1 到 100 之间的每页条数
     */
    private int resolvePageSize() {
        return Math.max(1, Math.min(properties.getPageSize(), MAX_PAGE_SIZE));
    }

    /**
     * 计算实际使用的最大分页数。
     *
     * @return 至少为 1 的最大分页数
     */
    private int resolveMaxPages() {
        return Math.max(1, properties.getMaxPages());
    }

    /**
     * 单页查询函数。
     *
     * @param <T> 数据类型
     */
    @FunctionalInterface
    private interface PageFetcher<T> {

        /**
         * 查询指定页。
         *
         * @param page    页码
         * @param perPage 每页条数
         * @return 当前页数据
         */
        List<T> fetch(int page, int perPage);
    }

    /**
     * 仓库 owner/repo 对。
     *
     * @param owner 仓库所有者
     * @param repo  仓库名
     */
    private record RepoName(String owner, String repo) {
    }

    /**
     * Gitee 用户响应，仅保留登录名。
     *
     * @param login 用户名
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GiteeUserResponse(String login) {
    }

    /**
     * Gitee 仓库列表响应，仅保留需要的字段。
     *
     * @param id                仓库 ID
     * @param fullName          仓库全名
     * @param htmlUrl           仓库网页地址
     * @param privateRepository 是否为私有仓库
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GiteeRepoResponse(
            Long id,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("private") Boolean privateRepository
    ) {
    }

    /**
     * Gitee 仓库详情响应，仅保留需要的字段。
     *
     * @param id       仓库 ID
     * @param fullName 仓库全名
     * @param htmlUrl  仓库网页地址
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GiteeRepoDetailResponse(
            Long id,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("html_url") String htmlUrl
    ) {
    }

    /**
     * Gitee README 响应。
     *
     * @param content  Base64 编码的内容
     * @param encoding 编码方式
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GiteeReadmeResponse(
            String content,
            String encoding
    ) {
    }
}
