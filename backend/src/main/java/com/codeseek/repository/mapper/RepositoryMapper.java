package com.codeseek.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeseek.repository.entity.Repository;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 仓库表 Mapper。
 *
 * <p>SQL 统一维护在 {@code resources/mapper/RepositoryMapper.xml} 中，
 * 接口只负责声明数据库操作，便于后续替换实现或增加自定义 SQL。</p>
 */
@Mapper
public interface RepositoryMapper extends BaseMapper<Repository> {

    /**
     * 新增一个仓库。
     *
     * <p>插入成功后，自增主键会回填到 {@code repository.id}。</p>
     *
     * @param repository 仓库实体
     * @return 影响行数
     */
    @Override
    int insert(Repository repository);

    /**
     * 批量新增仓库。
     *
     * @param repositories 仓库列表，不能为空
     * @return 影响行数
     */
    int batchInsert(@Param("repositories") List<Repository> repositories);

    /**
     * 根据数据库主键查询仓库。
     *
     * @param id 数据库主键
     * @return 仓库实体，不存在时返回 {@code null}
     */
    Repository selectById(@Param("id") Long id);

    /**
     * 根据 Gitee 仓库 ID 查询仓库。
     *
     * <p>数据库只对 repositoryId 建了普通索引，理论上可能存在重复数据，
     * 因此返回列表而不是单个对象。</p>
     *
     * @param repositoryId Gitee 仓库 ID
     * @return 仓库列表
     */
    List<Repository> selectByRepositoryId(@Param("repositoryId") Long repositoryId);

    /**
     * 根据仓库全名查询仓库。
     *
     * <p>fullName 未建唯一索引，因此返回列表。</p>
     *
     * @param fullName 仓库全名，格式为 {@code owner/repo}
     * @return 仓库列表
     */
    List<Repository> selectByFullName(@Param("fullName") String fullName);

    /**
     * 查询全部仓库，按主键升序排列。
     *
     * @return 仓库列表
     */
    List<Repository> selectAll();

    /**
     * 分页查询仓库，按主键升序排列。
     *
     * @param offset 偏移量，从 0 开始
     * @param limit  每页条数
     * @return 当前页仓库列表
     */
    List<Repository> selectPage(@Param("offset") long offset, @Param("limit") int limit);

    /**
     * 统计仓库总数。
     *
     * @return 仓库总数
     */
    long countAll();

    /**
     * 按主键更新仓库全部字段。
     *
     * @param repository 仓库实体，必须包含 id
     * @return 影响行数
     */
    int update(Repository repository);

    /**
     * 单独更新仓库 README 内容。
     *
     * @param id     数据库主键
     * @param readme README 文本
     * @return 影响行数
     */
    int updateReadme(@Param("id") Long id, @Param("readme") String readme);

    /**
     * 根据主键删除仓库。
     *
     * @param id 数据库主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
