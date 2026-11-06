package com.codeseek.semantic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Python 语义检索接口响应。
 *
 * @param results 语义检索结果列表
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SemanticSearchResponse(List<SemanticSearchItem> results) {
}
