package com.codeseek.semantic;

/**
 * 语义检索调用异常。
 */
public class SemanticSearchException extends RuntimeException {

    /**
     * 创建语义检索异常。
     *
     * @param message 异常说明
     * @param cause   原始异常
     */
    public SemanticSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
