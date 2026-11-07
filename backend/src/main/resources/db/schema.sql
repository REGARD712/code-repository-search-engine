SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for repository
-- ----------------------------
DROP TABLE IF EXISTS `repository`;
CREATE TABLE `repository`
(
    `id`           bigint                                                        NOT NULL AUTO_INCREMENT,
    `fullName`     varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `htmlUrl`      varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `readme`       longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci     NULL,
    `repositoryId` bigint                                                        NULL DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_fullName` (`fullName` ASC) USING BTREE,
    INDEX `idx_repositoryId` (`repositoryId` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 10325270
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '仓库表'
  ROW_FORMAT = DYNAMIC;