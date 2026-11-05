package com.codeseek.index.service;

import com.codeseek.index.model.IndexBuildResult;
import com.codeseek.index.model.RepositoryInvertedIndex;
import com.codeseek.index.model.RepositorySearchResult;

import java.util.List;

/**
 * 仓库倒排索引服务。
 */
public interface RepositoryIndexService {

    /**
     * 从 repository 表重建倒排索引并写入索引文件。
     *
     * @return 构建统计信息
     */
    IndexBuildResult rebuildIndex();

    /**
     * 使用倒排索引搜索仓库。
     *
     * @param keyword 查询关键词
     * @param limit   返回条数
     * @return 按词频排序的仓库列表
     */
    List<RepositorySearchResult> search(String keyword, int limit);

    /**
     * 从磁盘加载倒排索引。
     *
     * @return 加载后的倒排索引；索引文件不存在时返回空索引
     */
    RepositoryInvertedIndex loadIndex();
}
