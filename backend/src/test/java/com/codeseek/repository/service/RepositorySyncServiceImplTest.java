package com.codeseek.repository.service;

import com.codeseek.gitee.api.GiteeApi;
import com.codeseek.gitee.model.GiteeRepository;
import com.codeseek.repository.entity.Repository;
import com.codeseek.repository.mapper.RepositoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 仓库同步服务测试。
 *
 * <p>直接连接 application.yml 配置的 MySQL 数据库</p>
 */
@SpringBootTest
//@Transactional
class RepositorySyncServiceImplTest {

    @Autowired
    private RepositorySyncService repositorySyncService;

    @Autowired
    private RepositoryMapper repositoryMapper;

    @Autowired
    private GiteeApi giteeApi;

    /**
     * 测试syncRepositories
     *
     * @param
     * @return void
     */
    @Test
    void testSyncRepositories() {
        List<Repository> repositories = repositorySyncService.syncRepositories("rae-gard712");
        repositories.forEach(System.out::println);
    }

    /**
     * 验证用户仓库为空时会回退到组织仓库，并补全 README 后批量保存。
     */
    @Test
    void shouldFallbackToOrganizationAndSaveRepositories() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "demo-" + suffix;
        String fullNameA = username + "/repo-a-" + suffix;
        String fullNameB = username + "/repo-b-" + suffix;
        long repositoryIdA = System.nanoTime();
        long repositoryIdB = repositoryIdA + 1;

        when(giteeApi.listUserRepositories(username)).thenReturn(List.of());
        when(giteeApi.listOrganizationRepositories(username)).thenReturn(List.of(
                new GiteeRepository(fullNameA, "https://gitee.com/" + fullNameA, null, repositoryIdA),
                new GiteeRepository(fullNameB, "https://gitee.com/" + fullNameB, null, repositoryIdB)
        ));
        when(giteeApi.getRepositoryReadme(fullNameA)).thenReturn(List.of(
                new GiteeRepository(fullNameA, "https://gitee.com/" + fullNameA, "# README A", repositoryIdA)
        ));
        when(giteeApi.getRepositoryReadme(fullNameB)).thenReturn(List.of());

        List<Repository> result = repositorySyncService.syncRepositories(username);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReadme()).isEqualTo("# README A");
        assertThat(result.get(1).getReadme()).isNull();

        List<Repository> savedA = repositoryMapper.selectByRepositoryId(repositoryIdA);
        assertThat(savedA).hasSize(1);
        assertThat(savedA.get(0).getFullName()).isEqualTo(fullNameA);
        assertThat(savedA.get(0).getReadme()).isEqualTo("# README A");

        List<Repository> savedB = repositoryMapper.selectByRepositoryId(repositoryIdB);
        assertThat(savedB).hasSize(1);
        assertThat(savedB.get(0).getFullName()).isEqualTo(fullNameB);

        verify(giteeApi).listUserRepositories(username);
        verify(giteeApi).listOrganizationRepositories(username);
        verify(giteeApi).getRepositoryReadme(fullNameA);
        verify(giteeApi).getRepositoryReadme(fullNameB);
    }

    /**
     * 验证用户仓库存在时不会再查询同名组织。
     */
    @Test
    void shouldUseUserRepositoriesWithoutOrganizationFallback() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "alice-" + suffix;
        String fullName = username + "/repo-a-" + suffix;
        long repositoryId = System.nanoTime();

        when(giteeApi.listUserRepositories(username)).thenReturn(List.of(
                new GiteeRepository(fullName, "https://gitee.com/" + fullName, null, repositoryId)
        ));
        when(giteeApi.getRepositoryReadme(fullName)).thenReturn(List.of(
                new GiteeRepository(fullName, "https://gitee.com/" + fullName, "# A", repositoryId)
        ));

        List<Repository> result = repositorySyncService.syncRepositories(username);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReadme()).isEqualTo("# A");
        assertThat(repositoryMapper.selectByRepositoryId(repositoryId)).hasSize(1);
        verify(giteeApi, never()).listOrganizationRepositories(username);
    }
}
