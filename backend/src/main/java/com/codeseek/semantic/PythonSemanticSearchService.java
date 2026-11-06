package com.codeseek.semantic;

import com.codeseek.semantic.model.SemanticSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;

/**
 * 通过本地 Python 向量服务执行语义检索。
 *
 * <p>Python 服务加载 text2vec 模型和 FAISS 索引，Java 只负责通过 HTTP
 * 获取语义排序后的仓库 ID。</p>
 */
@Service
public class PythonSemanticSearchService implements SemanticSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PythonSemanticSearchService.class);

    private final RestClient restClient;

    private final String serviceBaseUrl;

    /**
     * 创建 Python 语义检索客户端。
     *
     * @param restClientBuilder Spring Boot 自动配置的 RestClient 构建器
     * @param baseUrl           Python 向量服务地址
     */
    @Autowired
    public PythonSemanticSearchService(
            RestClient.Builder restClientBuilder,
            @Value("${repository.semantic-search.base-url:http://127.0.0.1:8080}") String baseUrl
    ) {
        this(createRestClient(restClientBuilder, baseUrl), baseUrl);
    }

    /**
     * 使用外部构建好的 RestClient 创建服务，便于单元测试替换 HTTP 客户端。
     *
     * @param restClient      已构建的 RestClient
     * @param baseUrl         Python 服务地址
     */
    PythonSemanticSearchService(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.serviceBaseUrl = normalizeBaseUrl(baseUrl);
    }

    /**
     * 创建带超时配置的 RestClient。
     *
     * @param builder Spring Boot 的 RestClient 构建器
     * @param baseUrl Python 服务地址
     * @return RestClient
     */
    private static RestClient createRestClient(RestClient.Builder builder, String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(30_000);

        return builder
                .baseUrl(normalizeBaseUrl(baseUrl))
                .requestFactory(requestFactory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 去掉地址末尾斜杠。
     *
     * @param baseUrl 原始地址
     * @return 规范化地址
     */
    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> searchIds(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        long startedAt = System.nanoTime();
        try {
            SemanticSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", keyword)
                            .queryParam("topK", limit)
                            .build())
                    .retrieve()
                    .body(SemanticSearchResponse.class);

            if (response == null || response.results() == null) {
                LOGGER.info("语义检索完成：keyword='{}'，返回ID数=0，耗时={}ms",
                        keyword, elapsedMs(startedAt));
                return List.of();
            }

            List<Long> ids = response.results().stream()
                    .filter(Objects::nonNull)
                    .map(item -> item.id())
                    .filter(Objects::nonNull)
                    .toList();
            LOGGER.info("语义检索完成：keyword='{}'，返回ID数={}，耗时={}ms",
                    keyword, ids.size(), elapsedMs(startedAt));
            return ids;
        } catch (RestClientException exception) {
            LOGGER.warn("语义检索调用失败：keyword='{}'，服务地址={}，耗时={}ms，原因={}",
                    keyword, serviceBaseUrl, elapsedMs(startedAt), exception.getMessage());
            throw new SemanticSearchException(
                    "调用 Python 语义检索服务失败：" + serviceBaseUrl,
                    exception
            );
        }
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
}
