package com.codeseek.index.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 倒排索引配置装配。
 */
@Configuration
@EnableConfigurationProperties(RepositoryIndexProperties.class)
public class RepositoryIndexConfiguration {
}
