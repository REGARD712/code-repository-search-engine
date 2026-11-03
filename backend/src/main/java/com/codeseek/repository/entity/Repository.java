package com.codeseek.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仓库表实体。
 *
 * <p>对应 MySQL 表 {@code repository}。表中列名采用驼峰命名，
 * 与 Java 属性名保持一致，字段含义见各属性注释。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Repository {

    /**
     * 数据库主键，自增。
     */
    private Long id;

    /**
     * 仓库全名，格式为 {@code owner/repo}。
     */
    private String fullName;

    /**
     * 仓库在 Gitee 上的网页地址。
     */
    private String htmlUrl;

    /**
     * README 文件内容，可能为空。
     */
    private String readme;

    /**
     * Gitee 仓库 ID，用于和远端仓库对应。
     */
    private Long repositoryId;
}
