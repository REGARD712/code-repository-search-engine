package com.codeseek.gitee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Gitee API 客户端配置。
 *
 * <p>配置前缀为 {@code gitee.api}，支持通过 application.yml 或环境变量覆盖。
 * accessToken 为空时以匿名方式调用，受 Gitee 匿名接口限流约束。</p>
 */
@ConfigurationProperties(prefix = "gitee.api")
public class GiteeApiProperties {

    /**
     * Gitee API 基础地址。
     */
    private String baseUrl = "https://gitee.com/api/v5";

    /**
     * Gitee 个人访问令牌，可选；为空时匿名调用。
     */
    private String accessToken = "";

    /**
     * 每页条数，Gitee 单页最大 100，默认 100。
     */
    private int pageSize = 100;

    /**
     * 最大分页数，防止异常数据导致无限翻页，默认 1000 页。
     */
    private int maxPages = 1000;

    /**
     * 建立连接超时时间，默认 5 秒。
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 读取响应超时时间，默认 30 秒。
     */
    private Duration readTimeout = Duration.ofSeconds(30);

    /**
     * 获取 Gitee API 基础地址。
     *
     * @return 基础地址
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置 Gitee API 基础地址。
     *
     * @param baseUrl 基础地址
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 获取访问令牌。
     *
     * @return 访问令牌，可能为空
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * 设置访问令牌。
     *
     * @param accessToken 访问令牌
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * 获取每页条数。
     *
     * @return 每页条数
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页条数。
     *
     * @param pageSize 每页条数
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * 获取最大分页数。
     *
     * @return 最大分页数
     */
    public int getMaxPages() {
        return maxPages;
    }

    /**
     * 设置最大分页数。
     *
     * @param maxPages 最大分页数
     */
    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    /**
     * 获取连接超时时间。
     *
     * @return 连接超时时间
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * 设置连接超时时间。
     *
     * @param connectTimeout 连接超时时间
     */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * 获取读取超时时间。
     *
     * @return 读取超时时间
     */
    public Duration getReadTimeout() {
        return readTimeout;
    }

    /**
     * 设置读取超时时间。
     *
     * @param readTimeout 读取超时时间
     */
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
