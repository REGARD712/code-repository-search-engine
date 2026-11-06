package com.codeseek.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP 请求运行日志过滤器。
 *
 * <p>记录请求方法、路径、查询参数、响应状态、耗时和客户端 IP，
 * 不记录请求体，避免 README 等大文本进入日志。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final int MAX_QUERY_LOG_LENGTH = 500;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = abbreviate(request.getQueryString());
        String clientIp = request.getRemoteAddr();

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException exception) {
            LOGGER.error("HTTP 请求异常：{} {}，客户端={}", method, uri, clientIp, exception);
            throw exception;
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (query == null || query.isBlank()) {
                LOGGER.info("HTTP {} {} -> {}，耗时={}ms，客户端={}",
                        method, uri, response.getStatus(), elapsedMs, clientIp);
            } else {
                LOGGER.info("HTTP {} {}?{} -> {}，耗时={}ms，客户端={}",
                        method, uri, query, response.getStatus(), elapsedMs, clientIp);
            }
        }
    }

    /**
     * 限制日志中的查询串长度。
     *
     * @param value 原始内容
     * @return 截断后的内容
     */
    private String abbreviate(String value) {
        if (value == null || value.length() <= MAX_QUERY_LOG_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_QUERY_LOG_LENGTH) + "...";
    }
}
