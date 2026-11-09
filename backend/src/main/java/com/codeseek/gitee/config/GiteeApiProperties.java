package com.codeseek.gitee.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Gitee API 客户端配置。
 *
 * <p>配置前缀为 {@code gitee.api}，支持通过 application.yml 或环境变量覆盖。
 * accessToken 为空时以匿名方式调用，受 Gitee 匿名接口限流约束。</p>
 */
@Setter
@Getter
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

}
