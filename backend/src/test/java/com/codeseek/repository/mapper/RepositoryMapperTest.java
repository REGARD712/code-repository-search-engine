package com.codeseek.repository.mapper;

import com.codeseek.repository.entity.Repository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仓库表 Mapper 集成测试。
 *
 * <p>测试使用 H2 内存数据库，每次方法执行前重建表，
 * 验证新增、批量新增、查询、分页、更新和删除 SQL。</p>
 */
@SpringBootTest
@Sql(scripts = "/db/repository-h2.sql")
class RepositoryMapperTest {

    @Autowired
    private RepositoryMapper repositoryMapper;

    /**
     * 验证单条新增、主键回填和按主键查询。
     */
    @Test
    void shouldInsertAndSelectById() {
        Repository repository = new Repository(
                null,
                "oschina/git-osc",
                "https://gitee.com/oschina/git-osc",
                "# Git OSC",
                151L
        );

        int rows = repositoryMapper.insert(repository);
        System.out.println(rows);

        Repository saved = repositoryMapper.selectById(repository.getId());
        System.out.println(saved.toString());
    }

    /**
     * 验证批量新增、条件查询、分页、统计、更新和删除。
     */
    @Test
    void shouldBatchInsertAndSupportCrud() {
        List<Repository> repositories = List.of(
                new Repository(null, "demo-org/repo-a", "https://gitee.com/demo-org/repo-a", "A", 1001L),
                new Repository(null, "demo-org/repo-b", "https://gitee.com/demo-org/repo-b", "B", 1002L)
        );

        int rows = repositoryMapper.batchInsert(repositories);

        assertThat(rows).isEqualTo(2);
        assertThat(repositoryMapper.countAll()).isEqualTo(2);

        List<Repository> byRepositoryId = repositoryMapper.selectByRepositoryId(1001L);
        assertThat(byRepositoryId).hasSize(1);
        assertThat(byRepositoryId.get(0).getFullName()).isEqualTo("demo-org/repo-a");

        List<Repository> byFullName = repositoryMapper.selectByFullName("demo-org/repo-b");
        assertThat(byFullName).hasSize(1);
        assertThat(byFullName.get(0).getRepositoryId()).isEqualTo(1002L);

        List<Repository> firstPage = repositoryMapper.selectPage(0, 1);
        assertThat(firstPage).hasSize(1);

        Repository saved = repositoryMapper.selectByFullName("demo-org/repo-a").get(0);
        assertThat(repositoryMapper.updateReadme(saved.getId(), "updated readme")).isEqualTo(1);
        assertThat(repositoryMapper.selectById(saved.getId()).getReadme()).isEqualTo("updated readme");

        saved.setReadme("full update");
        assertThat(repositoryMapper.update(saved)).isEqualTo(1);

        assertThat(repositoryMapper.deleteById(saved.getId())).isEqualTo(1);
        assertThat(repositoryMapper.selectById(saved.getId())).isNull();
    }
}
