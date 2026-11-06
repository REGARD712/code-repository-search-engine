package com.codeseek.index.service;

import com.codeseek.index.builder.RepositoryIndexBuilder;
import com.codeseek.index.config.RepositoryIndexProperties;
import com.codeseek.index.model.IndexBuildResult;
import com.codeseek.index.model.RepositoryInvertedIndex;
import com.codeseek.index.model.RepositorySearchResult;
import com.codeseek.index.search.MultiWayIndexSearcher;
import com.codeseek.index.segment.JiebaTextSegmenter;
import com.codeseek.index.store.IndexFileStore;
import com.codeseek.repository.entity.Repository;
import com.codeseek.repository.mapper.RepositoryMapper;
import com.codeseek.semantic.SemanticSearchException;
import com.codeseek.semantic.SemanticSearchService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 仓库倒排索引服务实现。
 *
 * <p>使用 {@link AtomicReference} 保存当前索引，重建或加载时整体替换，
 * 搜索请求始终读取一个完整索引快照。</p>
 */
@Service
public class RepositoryIndexServiceImpl implements RepositoryIndexService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryIndexServiceImpl.class);

    /**
     * 单次检索最多合并的结果数。
     */
    private static final int MAX_SEARCH_LIMIT = 1000;

    /**
     * 未指定 limit 时的默认返回条数。
     */
    private static final int DEFAULT_SEARCH_LIMIT = 20;

    /**
     * 单个查询词最多扩展出的索引词数量，避免子串扫描产生过多候选。
     */
    private static final int MAX_EXPANDED_TERMS = 20;

    private final RepositoryMapper repositoryMapper;

    private final RepositoryIndexBuilder indexBuilder;

    private final MultiWayIndexSearcher indexSearcher;

    private final IndexFileStore indexFileStore;

    private final JiebaTextSegmenter textSegmenter;

    private final RepositoryIndexProperties properties;

    private final SemanticSearchService semanticSearchService;

    private final AtomicReference<RepositoryInvertedIndex> currentIndex =
            new AtomicReference<>(RepositoryInvertedIndex.empty());

    /**
     * 创建倒排索引服务。
     *
     * @param repositoryMapper 仓库 Mapper
     * @param indexBuilder     索引构建器
     * @param indexSearcher    多路归并检索器
     * @param indexFileStore        索引文件读写器
     * @param textSegmenter         结巴分词器
     * @param properties            索引配置
     * @param semanticSearchService AI 语义检索服务
     */
    public RepositoryIndexServiceImpl(
            RepositoryMapper repositoryMapper,
            RepositoryIndexBuilder indexBuilder,
            MultiWayIndexSearcher indexSearcher,
            IndexFileStore indexFileStore,
            JiebaTextSegmenter textSegmenter,
            RepositoryIndexProperties properties,
            SemanticSearchService semanticSearchService
    ) {
        this.repositoryMapper = repositoryMapper;
        this.indexBuilder = indexBuilder;
        this.indexSearcher = indexSearcher;
        this.indexFileStore = indexFileStore;
        this.textSegmenter = textSegmenter;
        this.properties = properties;
        this.semanticSearchService = semanticSearchService;
    }

    /**
     * 应用启动时加载已有索引文件。
     */
    @PostConstruct
    public void initialize() {
        loadIndex();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndexBuildResult rebuildIndex() {
        List<Repository> repositories = repositoryMapper.selectAll();
        RepositoryInvertedIndex rebuiltIndex = indexBuilder.build(repositories);
        Path indexPath = resolveIndexPath();

        try {
            indexFileStore.save(indexPath, rebuiltIndex);
        } catch (IOException exception) {
            throw new IllegalStateException("倒排索引文件写入失败：" + indexPath, exception);
        }

        currentIndex.set(rebuiltIndex);
        LOGGER.info("倒排索引重建完成：{}", indexPath);
        return new IndexBuildResult(
                rebuiltIndex.documentCount(),
                rebuiltIndex.termCount(),
                rebuiltIndex.postingCount()
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RepositorySearchResult> search(String keyword, int limit) {
        long startedAt = System.nanoTime();
        List<String> queryTerms = textSegmenter.segment(keyword);
        if (queryTerms.isEmpty()) {
            LOGGER.info("混合检索跳过：query='{}'，分词结果为空", keyword);
            return List.of();
        }

        int safeLimit = resolveSearchLimit(limit);
        List<String> effectiveTerms = expandQueryTerms(currentIndex.get(), queryTerms);
        if (effectiveTerms.isEmpty()) {
            LOGGER.info("混合检索完成：query='{}'，倒排候选=0，返回=0，耗时={}ms",
                    keyword, elapsedMs(startedAt));
            return List.of();
        }

        List<RepositorySearchResult> invertedResults =
                indexSearcher.search(currentIndex.get(), effectiveTerms, MAX_SEARCH_LIMIT);
        if (invertedResults.isEmpty()) {
            LOGGER.info("混合检索完成：query='{}'，倒排候选=0，返回=0，耗时={}ms",
                    keyword, elapsedMs(startedAt));
            return List.of();
        }

        List<Long> semanticIds;
        try {
            semanticIds = semanticSearchService.searchIds(keyword, MAX_SEARCH_LIMIT);
        } catch (SemanticSearchException exception) {
            // 语义服务不可用时降级为倒排结果，避免整个搜索接口不可用。
            LOGGER.warn("语义检索不可用，降级为倒排索引结果：query='{}'，倒排候选={}，原因={}",
                    keyword, invertedResults.size(), exception.getMessage());
            return invertedResults.stream()
                    .limit(safeLimit)
                    .toList();
        }

        // 取交集，并严格保持 AI 语义结果的顺序。
        Map<Long, RepositorySearchResult> invertedById = invertedResults.stream()
                .collect(Collectors.toMap(
                        RepositorySearchResult::id,
                        result -> result,
                        (left, right) -> left
                ));

        List<RepositorySearchResult> intersection = semanticIds.stream()
                .distinct()
                .map(invertedById::get)
                .filter(Objects::nonNull)
                .limit(safeLimit)
                .toList();
        LOGGER.info("混合检索完成：query='{}'，倒排候选={}，语义候选={}，交集返回={}，耗时={}ms",
                keyword, invertedResults.size(), semanticIds.size(), intersection.size(),
                elapsedMs(startedAt));
        return intersection;
    }

    /**
     * 对中文查询词做子串扩展。
     *
     * <p>结巴会把“搜索引擎”切成一个完整词项，因此查询“引擎”时
     * 精确匹配不到“搜索引擎”的倒排表。这里在精确词项不存在时，
     * 查找包含该查询词的中文索引词，例如“引擎”扩展到“搜索引擎”。</p>
     *
     * @param index      当前倒排索引
     * @param queryTerms 原始查询词
     * @return 实际参与倒排检索的词项
     */
    private List<String> expandQueryTerms(RepositoryInvertedIndex index, List<String> queryTerms) {
        if (index == null || queryTerms == null || queryTerms.isEmpty()) {
            return List.of();
        }

        Set<String> expandedTerms = new LinkedHashSet<>();
        for (String term : queryTerms) {
            if (term == null || term.isBlank()) {
                continue;
            }

            if (index.postings().containsKey(term)) {
                expandedTerms.add(term);
                continue;
            }

            // 只扩展长度至少为 2 的中文词，避免单字或英文子串产生过多噪声。
            if (!isChineseTerm(term) || term.length() < 2) {
                continue;
            }

            List<String> matchedTerms = index.postings().keySet().stream()
                    .filter(indexedTerm -> indexedTerm.contains(term))
                    .sorted()
                    .limit(MAX_EXPANDED_TERMS)
                    .toList();
            if (!matchedTerms.isEmpty()) {
                LOGGER.debug("查询词扩展：{} -> {}", term, matchedTerms);
                expandedTerms.addAll(matchedTerms);
            }
        }
        return List.copyOf(expandedTerms);
    }

    /**
     * 判断词项是否包含中文。
     *
     * @param term 词项
     * @return true 表示包含汉字
     */
    private boolean isChineseTerm(String term) {
        return term.codePoints()
                .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint)
                        == Character.UnicodeScript.HAN);
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
     * 处理搜索返回条数。
     *
     * @param limit 原始条数
     * @return 合法条数
     */
    private int resolveSearchLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_SEARCH_LIMIT);
    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public RepositoryInvertedIndex loadIndex() {
        Path indexPath = resolveIndexPath();
        if (!Files.exists(indexPath)) {
            LOGGER.info("倒排索引文件不存在，使用空索引：{}", indexPath);
            RepositoryInvertedIndex emptyIndex = RepositoryInvertedIndex.empty();
            currentIndex.set(emptyIndex);
            return emptyIndex;
        }

        try {
            RepositoryInvertedIndex loadedIndex = indexFileStore.load(indexPath);
            currentIndex.set(loadedIndex);
            LOGGER.info("倒排索引加载完成：{}，文档数={}，词项数={}，倒排项数={}",
                    indexPath, loadedIndex.documentCount(), loadedIndex.termCount(), loadedIndex.postingCount());
            return loadedIndex;
        } catch (IOException exception) {
            throw new IllegalStateException("倒排索引文件加载失败：" + indexPath, exception);
        }
    }

    /**
     * 解析实际索引路径，兼容 IDEA 将工作目录设为项目根目录的情况。
     *
     * <p>命令行从 backend 目录启动时，{@code ./data} 指向 backend/data；
     * IDEA 默认可能从项目根目录启动，此时配置的相对路径会落到
     * {@code 项目根/data}。如果该文件不存在，则尝试
     * {@code 项目根/backend/data}，避免误判索引文件缺失。</p>
     *
     * @return 实际使用的索引路径
     */
    private Path resolveIndexPath() {
        Path configuredPath = properties.getPath();
        if (configuredPath == null || configuredPath.isAbsolute()) {
            return configuredPath;
        }
        if (Files.exists(configuredPath)) {
            return configuredPath;
        }

        // 兼容从项目根目录启动：把相对路径再放到 backend 模块目录下查找。
        Path backendDirectory = Path.of("backend");
        Path backendRelativePath = backendDirectory.resolve(configuredPath).normalize();
        if (Files.exists(backendRelativePath)
                || (Files.isDirectory(backendDirectory) && !Files.isDirectory(configuredPath.getParent()))) {
            LOGGER.info("使用 backend 模块相对路径解析索引：{}", backendRelativePath);
            return backendRelativePath;
        }
        return configuredPath;
    }
}
