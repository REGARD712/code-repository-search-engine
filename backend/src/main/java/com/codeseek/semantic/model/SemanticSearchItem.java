package com.codeseek.semantic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Python 语义检索接口返回的单条结果。
 *
 * @param id      仓库数据库主键
 * @param score   语义相似度分数
 * @param fullName 仓库全名
 * @param htmlUrl 仓库网页地址
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SemanticSearchItem(
        Long id,
        Double score,
        String fullName,
        String htmlUrl
) {
}
