package com.codeseek.controller;

import com.codeseek.index.model.RepositorySearchResult;
import com.codeseek.index.service.RepositoryIndexService;
import com.codeseek.repository.entity.Repository;
import com.codeseek.repository.mapper.RepositoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仓库搜索 HTTP 接口。
 *
 * <p>接口使用倒排索引完成相关性排序，再按排序后的仓库 ID 批量读取
 * repository 表，返回包含 README 和 Gitee 仓库 ID 的完整仓库数据。</p>
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchController.class);

    /**
     * 单次请求最多返回条数。
     */
    private static final int MAX_RESULT_COUNT = 1000;

    private final RepositoryIndexService repositoryIndexService;

    private final RepositoryMapper repositoryMapper;

    /**
     * 创建搜索控制器。
     *
     * @param repositoryIndexService 倒排索引服务
     * @param repositoryMapper       仓库 Mapper
     */
    public SearchController(
            RepositoryIndexService repositoryIndexService,
            RepositoryMapper repositoryMapper
    ) {
        this.repositoryIndexService = repositoryIndexService;
        this.repositoryMapper = repositoryMapper;
    }

    /**
     * 根据查询词搜索仓库。
     *
     * <p>支持两种传参方式：</p>
     * <ul>
     *     <li>GET 请求体：{@code {"query":"数据结构"}}</li>
     *     <li>GET 查询参数：{@code /search?query=数据结构}</li>
     * </ul>
     *
     * @param request 请求体，可为空
     * @param query   查询参数，请求体没有 query 时使用
     * @return 仓库列表，最多 1000 条
     */
    @GetMapping(produces = "application/json;charset=UTF-8")
    public List<Repository> search(
            @RequestBody(required = false) SearchRequest request,
            @RequestParam(name = "query", required = false) String query
    ) {
        long startedAt = System.nanoTime();
        String keyword = resolveKeyword(request, query);
        if (!StringUtils.hasText(keyword)) {
            LOGGER.info("搜索请求忽略：查询词为空");
            return List.of();
        }

        List<RepositorySearchResult> rankedResults =
                repositoryIndexService.search(keyword.trim(), MAX_RESULT_COUNT);
        if (rankedResults.isEmpty()) {
            LOGGER.info("搜索完成：query='{}'，混合检索无结果，耗时={}ms",
                    keyword, elapsedMs(startedAt));
            return List.of();
        }

        List<Long> repositoryIds = rankedResults.stream()
                .map(RepositorySearchResult::id)
                .toList();

        // 一次批量查询补齐完整仓库字段，SQL 内部按传入 ID 顺序返回。
        List<Repository> repositories = repositoryMapper.selectByIdsInOrder(repositoryIds)
                .stream()
                .limit(MAX_RESULT_COUNT)
                .toList();

        // 对readme字段进行截取
        repositories.forEach(repository -> {
            if (StringUtils.hasText(repository.getReadme())) {
                repository.setReadme(repository.getReadme().substring(0, 200));
            }
        });

        LOGGER.info("搜索完成：query='{}'，候选={}，返回={}，耗时={}ms",
                keyword, rankedResults.size(), repositories.size(), elapsedMs(startedAt));
        return repositories;
    }

    /**
     * 计算从开始到当前的毫秒数。
     *
     * @param startedAt 开始时间，单位纳秒
     * @return 耗时毫秒数
     */
    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /**
     * 从请求体或查询参数中解析关键词。
     *
     * @param request 请求体
     * @param query   查询参数
     * @return 去掉首尾空白的关键词
     */
    private String resolveKeyword(SearchRequest request, String query) {
        if (request != null && StringUtils.hasText(request.query())) {
            return request.query().trim();
        }
        return query == null ? "" : query.trim();
    }
}
