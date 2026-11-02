package com.codeseek.gitee.exception;

/**
 * Gitee 接口调用异常。
 *
 * <p>统一包装网络异常、HTTP 错误和响应解析异常，便于上层按需处理，
 * 避免业务代码直接依赖 Spring 的 HTTP 异常类型。</p>
 */
public class GiteeApiException extends RuntimeException {

    /**
     * HTTP 状态码；未拿到响应时为 -1。
     */
    private final int statusCode;

    /**
     * Gitee 返回的原始响应体，便于排查限流、权限等问题。
     */
    private final String responseBody;

    /**
     * 创建不带 HTTP 状态码的异常。
     *
     * @param message 异常说明
     */
    public GiteeApiException(String message) {
        super(message);
        this.statusCode = -1;
        this.responseBody = null;
    }

    /**
     * 创建不带 HTTP 状态码的异常。
     *
     * @param message 异常说明
     * @param cause   原始异常
     */
    public GiteeApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    /**
     * 创建带 HTTP 状态码和响应体的异常。
     *
     * @param message      异常说明
     * @param statusCode   HTTP 状态码
     * @param responseBody 原始响应体
     * @param cause        原始异常
     */
    public GiteeApiException(String message, int statusCode, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * 获取 HTTP 状态码。
     *
     * @return HTTP 状态码，未知时为 -1
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 获取 Gitee 原始响应体。
     *
     * @return 响应体，未知时为 {@code null}
     */
    public String getResponseBody() {
        return responseBody;
    }
}
