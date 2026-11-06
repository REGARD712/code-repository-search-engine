package com.codeseek.repository.mapper;

import com.codeseek.repository.entity.Repository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仓库表 Mapper 集成测试。
 *
 * <p>测试直接连接 application.yml 配置的 MySQL 数据库，
 * 使用事务回滚保证测试数据不会污染实际数据。运行前需先执行 schema.sql 建表。</p>
 */
@SpringBootTest
@Transactional
class RepositoryMapperTest {

    @Autowired
    private RepositoryMapper repositoryMapper;

    /**
     * 验证单条新增、主键回填和按主键查询。
     */
    @Test
    void shouldInsertAndSelectById() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Repository repository = new Repository(
                null,
                "test-owner/repo-" + suffix,
                "https://gitee.com/test-owner/repo-" + suffix,
                "# Git OSC",
                System.nanoTime()
        );

        int rows = repositoryMapper.insert(repository);

        assertThat(rows).isEqualTo(1);
        assertThat(repository.getId()).isNotNull();
        System.out.println(rows);

        Repository saved = repositoryMapper.selectById(repository.getId());
        assertThat(saved).isNotNull();
        assertThat(saved.getFullName()).isEqualTo(repository.getFullName());
        assertThat(saved.getReadme()).isEqualTo("# Git OSC");
        assertThat(saved.getRepositoryId()).isEqualTo(repository.getRepositoryId());
        System.out.println(saved.toString());
    }

    /**
     * 验证批量新增、条件查询、分页、统计、更新和删除。
     */
    @Test
    void shouldBatchInsertAndSupportCrud() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long repositoryIdA = System.nanoTime();
        long repositoryIdB = repositoryIdA + 1;

        List<Repository> repositories = List.of(
                new Repository(null, "test-org/repo-a-" + suffix,
                        "https://gitee.com/test-org/repo-a-" + suffix, "A", repositoryIdA),
                new Repository(null, "test-org/repo-b-" + suffix,
                        "https://gitee.com/test-org/repo-b-" + suffix, "B", repositoryIdB)
        );

        long countBefore = repositoryMapper.countAll();
        int rows = repositoryMapper.batchInsert(repositories);

        assertThat(rows).isEqualTo(2);
        assertThat(repositoryMapper.countAll()).isGreaterThanOrEqualTo(countBefore + 2);

        List<Repository> byRepositoryId = repositoryMapper.selectByRepositoryId(repositoryIdA);
        assertThat(byRepositoryId).hasSize(1);
        assertThat(byRepositoryId.get(0).getFullName()).isEqualTo("test-org/repo-a-" + suffix);

        List<Repository> byFullName = repositoryMapper.selectByFullName("test-org/repo-b-" + suffix);
        assertThat(byFullName).hasSize(1);
        assertThat(byFullName.get(0).getRepositoryId()).isEqualTo(repositoryIdB);

        List<Repository> firstPage = repositoryMapper.selectPage(0, 1);
        assertThat(firstPage).hasSize(1);

        Repository saved = repositoryMapper.selectByFullName("test-org/repo-a-" + suffix).get(0);
        assertThat(repositoryMapper.updateReadme(saved.getId(), "updated readme")).isEqualTo(1);
        assertThat(repositoryMapper.selectById(saved.getId()).getReadme()).isEqualTo("updated readme");

        saved.setReadme("full update");
        assertThat(repositoryMapper.update(saved)).isEqualTo(1);

        assertThat(repositoryMapper.deleteById(saved.getId())).isEqualTo(1);
        assertThat(repositoryMapper.selectById(saved.getId())).isNull();
    }

    /**
     * 验证批量查询结果与入参 ID 的顺序一致。
     */
    @Test
    void shouldSelectByIdsInOrder() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Repository first = new Repository(
                null,
                "test-order/first-" + suffix,
                "https://gitee.com/test-order/first-" + suffix,
                "first",
                System.nanoTime()
        );
        Repository second = new Repository(
                null,
                "test-order/second-" + suffix,
                "https://gitee.com/test-order/second-" + suffix,
                "second",
                System.nanoTime()
        );

        assertThat(repositoryMapper.insert(first)).isEqualTo(1);
        assertThat(repositoryMapper.insert(second)).isEqualTo(1);

        // 故意按与数据库主键相反的顺序查询。
        List<Repository> results = repositoryMapper.selectByIdsInOrder(
                List.of(second.getId(), first.getId())
        );

        assertThat(results)
                .extracting(Repository::getId)
                .containsExactly(second.getId(), first.getId());
    }
}
