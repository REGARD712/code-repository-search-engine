package com.codeseek;

import com.codeseek.gitee.api.GiteeApi;
import com.codeseek.gitee.model.GiteeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Spring Boot 上下文加载测试。
 */
@SpringBootTest
class SearchEngineApplicationTests {

    @Autowired
    private GiteeApi giteeApi;

    /**
     * 验证searchUser方法是否正常工作。
     */
    @Test
    void testSearchUser() {
        List<String> users = giteeApi.searchUserNames("oo");
        users.forEach(System.out::println);
    }

    /**
     * 验证listUserRepositories方法是否正常工作。
     *
     * @param
     * @return void
     */
    @Test
    void testSearchRepository() {
        List<GiteeRepository> repositories = giteeApi.listUserRepositories("whisper-void");
        repositories.forEach(System.out::println);
    }

    /**
     * 验证listOrganizationRepositories方法是否正常工作。
     *
     * @param
     * @return void
     */
    @Test
    void testSearchOrgRepository() {
        List<GiteeRepository> orgRepositories = giteeApi.listOrganizationRepositories("didiopensource");
        orgRepositories.forEach(System.out::println);
    }

    /**
     * 验证getRepositoryReadme方法是否正常工作。
     *
     * @param
     * @return void
     */
    @Test
    void testSearchRepositoryReadme() {
        List<GiteeRepository> repositoryReadme = giteeApi.getRepositoryReadme("didiopensource/TrafficIndex");
        repositoryReadme.forEach(System.out::println);
    }
}
