-- H2 测试库仓库表结构，仅用于 Mapper 集成测试。
DROP TABLE IF EXISTS `repository`;

CREATE TABLE `repository`
(
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `fullName`     VARCHAR(255) NOT NULL,
    `htmlUrl`      VARCHAR(255) NOT NULL,
    `readme`       CLOB,
    `repositoryId` BIGINT
);
