package com.codeseek.repository.service;

import com.codeseek.repository.entity.Repository;

import java.util.List;

/**
 * 仓库同步服务。
 *
 * <p>以用户名为入口，依次完成 Gitee 仓库发现、README 补全和数据库批量保存。
 * 接口只暴露一个同步方法，具体实现由 {@code RepositorySyncServiceImpl} 提供。</p>
 */
public interface RepositorySyncService {

    /**
     * 按用户名同步仓库并保存到数据库。
     *
     * <p>处理流程：</p>
     * <ol>
     *     <li>调用 {@code listUserRepositories(username)} 查询用户仓库；</li>
     *     <li>如果用户仓库为空，则把用户名当作组织名，调用
     *         {@code listOrganizationRepositories(username)}；</li>
     *     <li>逐个仓库调用 {@code getRepositoryReadme} 补全 README；</li>
     *     <li>将完整结果批量保存到 {@code repository} 表。</li>
     * </ol>
     *
     * <p>整个过程采用单线程顺序执行，避免并发请求触发 Gitee 限流。</p>
     *
     * @param username 用户名，同时作为组织名兜底查询；不能为空
     * @return 本次获取并保存的仓库列表；没有查询到仓库时返回空列表
     */
    List<Repository> syncRepositories(String username);
}
