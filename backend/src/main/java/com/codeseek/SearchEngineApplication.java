package com.codeseek;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 代码仓库搜索引擎启动类。
 *
 * <p>启动 Spring Boot 应用，并自动装配 Gitee API 客户端等基础组件。</p>
 */
@SpringBootApplication
public class SearchEngineApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SearchEngineApplication.class, args);
    }
}
