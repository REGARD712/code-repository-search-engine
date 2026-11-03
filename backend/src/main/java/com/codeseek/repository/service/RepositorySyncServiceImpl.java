package com.codeseek.repository.service;

import com.codeseek.gitee.api.GiteeApi;
import com.codeseek.gitee.model.GiteeRepository;
import com.codeseek.repository.entity.Repository;
import com.codeseek.repository.mapper.RepositoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 仓库同步服务实现。
 *
 * <p>按照“用户仓库优先、组织仓库兜底”的顺序获取仓库，再逐个补全 README，
 * 最后一次性批量写入数据库。全程使用普通 for 循环，保持单线程顺序执行。</p>
 */
@Service
public class RepositorySyncServiceImpl implements RepositorySyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositorySyncServiceImpl.class);

    private final GiteeApi giteeApi;

    private final RepositoryMapper repositoryMapper;

    /**
     * 创建仓库同步服务。
     *
     * @param giteeApi          Gitee 接口
     * @param repositoryMapper  仓库表 Mapper
     */
    public RepositorySyncServiceImpl(GiteeApi giteeApi, RepositoryMapper repositoryMapper) {
        this.giteeApi = Objects.requireNonNull(giteeApi, "giteeApi 不能为空");
        this.repositoryMapper = Objects.requireNonNull(repositoryMapper, "repositoryMapper 不能为空");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Repository> syncRepositories(String username) {
        String normalizedUsername = requireText(username, "用户名");

        // 第一步：优先按用户查询仓库。
        List<GiteeRepository> giteeRepositories = giteeApi.listUserRepositories(normalizedUsername);
        if (giteeRepositories == null || giteeRepositories.isEmpty()) {
            // 第二步：用户仓库为空时，把同一个名称当作组织名再查一次。
            LOGGER.info("用户仓库为空，尝试按组织查询：{}", normalizedUsername);
            giteeRepositories = giteeApi.listOrganizationRepositories(normalizedUsername);
        }

        if (giteeRepositories == null || giteeRepositories.isEmpty()) {
            LOGGER.info("未查询到任何仓库：{}", normalizedUsername);
            return List.of();
        }

        // 第三步：单线程逐个补全 README，并转换成数据库实体。
        List<Repository> repositories = new ArrayList<>(giteeRepositories.size());
        for (GiteeRepository giteeRepository : giteeRepositories) {
            String readme = loadReadme(normalizedUsername, giteeRepository);
            repositories.add(new Repository(
                    null,
                    giteeRepository.fullName(),
                    giteeRepository.htmlUrl(),
                    readme,
                    giteeRepository.repositoryId()
            ));
        }

        // 第四步：一次性批量保存；单条批量 SQL 本身具备原子性。
        int affectedRows = repositoryMapper.batchInsert(repositories);
        LOGGER.info("仓库同步完成：用户名={}，查询到={}，写入={}",
                normalizedUsername, repositories.size(), affectedRows);

        return List.copyOf(repositories);
    }

    /**
     * 根据用户名和仓库名查询 README。
     *
     * @param username         用户名/组织名
     * @param giteeRepository  Gitee 仓库元数据
     * @return README 文本；仓库没有 README 时返回 {@code null}
     */
    private String loadReadme(String username, GiteeRepository giteeRepository) {
        String repositoryName = extractRepositoryName(giteeRepository.fullName());
        String repositoryFullName = username + "/" + repositoryName;

        List<GiteeRepository> readmeRepositories = giteeApi.getRepositoryReadme(repositoryFullName);
        if (readmeRepositories == null || readmeRepositories.isEmpty()) {
            return null;
        }

        return readmeRepositories.stream()
                .map(GiteeRepository::readme)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 从 owner/repo 形式的仓库全名中截取仓库名。
     *
     * @param fullName 仓库全名
     * @return 仓库名
     */
    private String extractRepositoryName(String fullName) {
        String normalizedFullName = requireText(fullName, "仓库全名");
        int separatorIndex = normalizedFullName.indexOf('/');
        if (separatorIndex <= 0 || separatorIndex >= normalizedFullName.length() - 1) {
            throw new IllegalArgumentException("仓库全名必须为 owner/repo 格式：" + fullName);
        }
        return normalizedFullName.substring(separatorIndex + 1);
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
}
