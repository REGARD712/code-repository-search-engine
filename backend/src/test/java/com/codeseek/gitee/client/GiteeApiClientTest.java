package com.codeseek.gitee.client;

import com.codeseek.gitee.api.GiteeApi;
import com.codeseek.gitee.config.GiteeApiProperties;
import com.codeseek.gitee.exception.GiteeApiException;
import com.codeseek.gitee.model.GiteeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/**
 * Gitee API 客户端测试。
 *
 * <p>使用 MockRestServiceServer 模拟 Gitee 响应，重点验证：
 * 分页是否取全量、字段映射是否正确、README 是否解码、404 是否按空列表处理。</p>
 */
class GiteeApiClientTest {

    private MockRestServiceServer server;

    private GiteeApi giteeApi;

    /**
     * 每个测试前创建带模拟 HTTP 服务的客户端。
     */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://gitee.test/api/v5");

        server = MockRestServiceServer.bindTo(builder).build();

        GiteeApiProperties properties = new GiteeApiProperties();
        properties.setBaseUrl("https://gitee.test/api/v5");
        properties.setAccessToken("test-token");
        properties.setPageSize(2);

        giteeApi = new GiteeApiClient(builder.build(), properties);
    }

    /**
     * 验证用户搜索会翻页并只返回用户名。
     */
    @Test
    void shouldSearchAllUserNamesAcrossPages() {
        server.expect(requestTo(containsString("/search/users")))
                .andExpect(queryParam("q", "spring"))
                .andExpect(queryParam("page", "1"))
                .andExpect(queryParam("per_page", "2"))
                .andExpect(queryParam("access_token", "test-token"))
                .andRespond(withSuccess("""
                        [
                          {"id": 1, "login": "alice", "name": "Alice"},
                          {"id": 2, "login": "bob", "name": "Bob"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(containsString("/search/users")))
                .andExpect(queryParam("page", "2"))
                .andRespond(withSuccess("""
                        [
                          {"id": 3, "login": "carol", "name": "Carol"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<String> names = giteeApi.searchUserNames("spring");

        assertEquals(List.of("alice", "bob", "carol"), names);
        server.verify();
    }

    /**
     * 验证用户仓库会翻页并映射为统一仓库模型。
     */
    @Test
    void shouldListAllUserRepositories() {
        server.expect(requestTo(containsString("/users/alice/repos")))
                .andExpect(queryParam("type", "all"))
                .andExpect(queryParam("sort", "full_name"))
                .andExpect(queryParam("page", "1"))
                .andExpect(queryParam("per_page", "2"))
                .andRespond(withSuccess("""
                        [
                          {"id": 101, "full_name": "alice/repo-a", "html_url": "https://gitee.com/alice/repo-a", "private": false},
                          {"id": 102, "full_name": "alice/repo-b", "html_url": "https://gitee.com/alice/repo-b", "private": false}
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(containsString("/users/alice/repos")))
                .andExpect(queryParam("page", "2"))
                .andRespond(withSuccess("""
                        [
                          {"id": 103, "full_name": "alice/repo-c", "html_url": "https://gitee.com/alice/repo-c"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<GiteeRepository> repositories = giteeApi.listUserRepositories("alice");

        assertEquals(3, repositories.size());
        assertEquals("alice/repo-a", repositories.get(0).fullName());
        assertEquals(101L, repositories.get(0).repositoryId());
        assertEquals("https://gitee.com/alice/repo-c", repositories.get(2).htmlUrl());
        assertNull(repositories.get(0).readme());
        server.verify();
    }

    /**
     * 验证带 Token 查询时仍会过滤私有仓库。
     */
    @Test
    void shouldIgnorePrivateRepositories() {
        server.expect(requestTo(containsString("/users/alice/repos")))
                .andExpect(queryParam("page", "1"))
                .andRespond(withSuccess("""
                        [
                          {"id": 301, "full_name": "alice/public-repo", "html_url": "https://gitee.com/alice/public-repo", "private": false},
                          {"id": 302, "full_name": "alice/private-repo", "html_url": "https://gitee.com/alice/private-repo", "private": true}
                        ]
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(containsString("/users/alice/repos")))
                .andExpect(queryParam("page", "2"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<GiteeRepository> repositories = giteeApi.listUserRepositories("alice");

        assertEquals(1, repositories.size());
        assertEquals("alice/public-repo", repositories.get(0).fullName());
        server.verify();
    }

    /**
     * 验证组织仓库只请求公开仓库，并正确映射字段。
     */
    @Test
    void shouldListOrganizationRepositories() {
        server.expect(requestTo(containsString("/orgs/demo-org/repos")))
                .andExpect(queryParam("type", "public"))
                .andExpect(queryParam("page", "1"))
                .andRespond(withSuccess("""
                        [
                          {"id": 201, "full_name": "demo-org/platform", "html_url": "https://gitee.com/demo-org/platform"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<GiteeRepository> repositories = giteeApi.listOrganizationRepositories("demo-org");

        assertEquals(1, repositories.size());
        assertEquals("demo-org/platform", repositories.get(0).fullName());
        assertEquals(201L, repositories.get(0).repositoryId());
        server.verify();
    }

    /**
     * 验证 README 会解码 Base64，并通过仓库详情补全 ID 和网页地址。
     */
    @Test
    void shouldDecodeReadmeAndFillRepositoryFields() {
        server.expect(requestTo(containsString("/repos/oschina/git-osc/readme")))
                .andRespond(withSuccess("""
                        {
                          "encoding": "base64",
                          "content": "SGVsbG8gR2l0ZWUh"
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(containsString("/repos/oschina/git-osc")))
                .andRespond(withSuccess("""
                        {
                          "id": 151,
                          "full_name": "oschina/git-osc",
                          "html_url": "https://gitee.com/oschina/git-osc"
                        }
                        """, MediaType.APPLICATION_JSON));

        List<GiteeRepository> repositories = giteeApi.getRepositoryReadme("oschina/git-osc");

        assertEquals(1, repositories.size());
        assertEquals("oschina/git-osc", repositories.get(0).fullName());
        assertEquals("Hello Gitee!", repositories.get(0).readme());
        assertEquals(151L, repositories.get(0).repositoryId());
        server.verify();
    }

    /**
     * 验证仓库没有 README 时返回空列表，而不是抛出异常。
     */
    @Test
    void shouldReturnEmptyWhenReadmeNotFound() {
        server.expect(requestTo(containsString("/repos/oschina/git-osc/readme")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        List<GiteeRepository> repositories = giteeApi.getRepositoryReadme("oschina/git-osc");

        assertTrue(repositories.isEmpty());
        server.verify();
    }

    /**
     * 验证仓库全名格式非法时直接拒绝请求。
     */
    @Test
    void shouldRejectInvalidRepositoryFullName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> giteeApi.getRepositoryReadme("invalid-repository-name")
        );

        assertTrue(exception.getMessage().contains("owner/repo"));
    }

    /**
     * 验证 HTTP 错误会转换为统一的 GiteeApiException。
     */
    @Test
    void shouldWrapHttpError() {
        server.expect(requestTo(containsString("/search/users")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"message\":\"bad token\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        GiteeApiException exception = assertThrows(
                GiteeApiException.class,
                () -> giteeApi.searchUserNames("spring")
        );

        assertEquals(401, exception.getStatusCode());
        assertTrue(exception.getResponseBody().contains("bad token"));
    }
}
