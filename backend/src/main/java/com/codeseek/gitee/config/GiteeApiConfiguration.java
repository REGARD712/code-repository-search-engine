package com.codeseek.gitee.config;

import com.codeseek.gitee.api.GiteeApi;
import com.codeseek.gitee.client.GiteeApiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Gitee API 客户端装配配置。
 *
 * <p>负责把配置属性、HTTP 客户端和接口实现装配为 Spring Bean。
 * 业务层注入 {@link GiteeApi} 即可使用，不感知底层 HTTP 实现。</p>
 */
@Configuration
@EnableConfigurationProperties(GiteeApiProperties.class)
public class GiteeApiConfiguration {

    /**
     * 创建 Gitee API 接口实现。
     *
     * @param restClientBuilder Spring Boot 自动配置的 RestClient 构建器
     * @param properties        Gitee API 配置
     * @return 面向业务层的 Gitee API 接口
     */
    @Bean
    public GiteeApi giteeApi(RestClient.Builder restClientBuilder, GiteeApiProperties properties) {
        // 使用 JDK 自带的 HttpURLConnection 实现，避免额外引入 HTTP 客户端依赖。
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());

        // 去掉 baseUrl 末尾斜杠，避免拼接出双斜杠路径。
        String baseUrl = properties.getBaseUrl() == null
                ? ""
                : properties.getBaseUrl().replaceAll("/+$", "");

        RestClient restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "code-repository-search-engine/0.0.1")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        return new GiteeApiClient(restClient, properties);
    }
}
