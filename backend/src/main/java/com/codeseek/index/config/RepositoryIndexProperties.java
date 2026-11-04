package com.codeseek.index.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 仓库倒排索引配置。
 *
 * <p>配置前缀为 {@code repository.index}。</p>
 */
@ConfigurationProperties(prefix = "repository.index")
public class RepositoryIndexProperties {

    /**
     * 倒排索引二进制文件路径。
     */
    private Path path = Path.of("data", "repository-index.bin");

    /**
     * 获取索引文件路径。
     *
     * @return 索引文件路径
     */
    public Path getPath() {
        return path;
    }

    /**
     * 设置索引文件路径。
     *
     * @param path 索引文件路径
     */
    public void setPath(Path path) {
        this.path = path;
    }
}
