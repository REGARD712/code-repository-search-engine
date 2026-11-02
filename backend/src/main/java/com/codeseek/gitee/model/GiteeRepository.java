package com.codeseek.gitee.model;

/**
 * Gitee 仓库统一模型。
 *
 * <p>按照当前搜索场景的需要，只保留四个字段：仓库全名、仓库网页地址、README 内容和仓库 ID。
 * 用户仓库、组织仓库接口只会返回仓库元数据，此时 {@code readme} 为 {@code null}；
 * README 接口会额外补全 {@code readme}。</p>
 *
 * @param fullName     仓库全名，格式为 {@code owner/repo}
 * @param htmlUrl      仓库在 Gitee 上的网页地址
 * @param readme       README 文件解码后的文本内容，未查询时为 {@code null}
 * @param repositoryId Gitee 仓库 ID
 */
public record GiteeRepository(
        String fullName,
        String htmlUrl,
        String readme,
        Long repositoryId
) {
}
