-- ============================================================
-- 代码仓库搜索引擎数据库初始化脚本
-- 说明：原始 DDL 中索引定义写在了字段列表内部，属于无效语法；
--       本脚本保留原字段名和索引，仅修正索引声明位置与格式。
-- ============================================================
CREATE DATABASE IF NOT EXISTS `search_engine`
    DEFAULT CHARACTER SET utf8mb4;

USE `search_engine`;

CREATE TABLE IF NOT EXISTS `repository`
(
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `fullName`     VARCHAR(255) NOT NULL COMMENT '仓库全名，owner/repo',
    `htmlUrl`      VARCHAR(255) NOT NULL COMMENT '仓库网页地址',
    `readme`       LONGTEXT NULL COMMENT 'README 文本内容',
    `repositoryId` BIGINT NULL COMMENT 'Gitee 仓库 ID',
    INDEX `idx_fullName` (`fullName`),
    INDEX `idx_repositoryId` (`repositoryId`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 10000001
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT = '仓库表';
