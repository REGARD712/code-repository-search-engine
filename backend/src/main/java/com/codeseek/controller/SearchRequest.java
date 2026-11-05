package com.codeseek.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 搜索请求体。
 *
 * @param query 查询关键词
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchRequest(String query) {
}
