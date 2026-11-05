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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 仓库倒排索引服务实现。
 *
 * <p>使用 {@link AtomicReference} 保存当前索引，重建或加载时整体替换，
 * 搜索请求始终读取一个完整索引快照。</p>
 */
@Service
public class RepositoryIndexServiceImpl implements RepositoryIndexService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryIndexServiceImpl.class);

    private final RepositoryMapper repositoryMapper;

    private final RepositoryIndexBuilder indexBuilder;

    private final MultiWayIndexSearcher indexSearcher;

    private final IndexFileStore indexFileStore;

    private final JiebaTextSegmenter textSegmenter;

    private final RepositoryIndexProperties properties;

    private final AtomicReference<RepositoryInvertedIndex> currentIndex =
            new AtomicReference<>(RepositoryInvertedIndex.empty());

    /**
     * 创建倒排索引服务。
     *
     * @param repositoryMapper 仓库 Mapper
     * @param indexBuilder     索引构建器
     * @param indexSearcher    多路归并检索器
     * @param indexFileStore   索引文件读写器
     * @param textSegmenter    结巴分词器
     * @param properties       索引配置
     */
    public RepositoryIndexServiceImpl(
            RepositoryMapper repositoryMapper,
            RepositoryIndexBuilder indexBuilder,
            MultiWayIndexSearcher indexSearcher,
            IndexFileStore indexFileStore,
            JiebaTextSegmenter textSegmenter,
            RepositoryIndexProperties properties
    ) {
        this.repositoryMapper = repositoryMapper;
        this.indexBuilder = indexBuilder;
        this.indexSearcher = indexSearcher;
        this.indexFileStore = indexFileStore;
        this.textSegmenter = textSegmenter;
        this.properties = properties;
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

        try {
            indexFileStore.save(properties.getPath(), rebuiltIndex);
        } catch (IOException exception) {
            throw new IllegalStateException("倒排索引文件写入失败：" + properties.getPath(), exception);
        }

        currentIndex.set(rebuiltIndex);
        LOGGER.info("倒排索引重建完成：{}", properties.getPath());
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
        List<String> queryTerms = textSegmenter.segment(keyword);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        return indexSearcher.search(currentIndex.get(), queryTerms, limit);
    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public RepositoryInvertedIndex loadIndex() {
        if (!Files.exists(properties.getPath())) {
            LOGGER.info("倒排索引文件不存在，使用空索引：{}", properties.getPath());
            currentIndex.set(RepositoryInvertedIndex.empty());
            return null;
        }

        try {
            RepositoryInvertedIndex loadedIndex = indexFileStore.load(properties.getPath());
            currentIndex.set(loadedIndex);
            LOGGER.info("倒排索引加载完成：文档数={}，词项数={}，倒排项数={}",
                    loadedIndex.documentCount(), loadedIndex.termCount(), loadedIndex.postingCount());
        } catch (IOException exception) {
            throw new IllegalStateException("倒排索引文件加载失败：" + properties.getPath(), exception);
        }
        return null;
    }
}
