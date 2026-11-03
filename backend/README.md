# Code Repository Search Engine — Backend

代码仓库搜索引擎的后端项目，已搭好 Spring Boot 基础结构，封装了 Gitee 用户、仓库和 README 查询接口，并生成了仓库表的实体与 MyBatis Mapper；暂不包含业务 Controller 和前端联调代码。

## 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| Spring Boot | 3.3.3 |
| Java | 17（Spring Boot 3 最低要求） |
| Maven | 3.8.1（通过 Maven Wrapper 固定） |
| Web | Spring Boot Starter Web |
| 持久层 | MyBatis 3.5.x（mybatis-spring-boot-starter 3.0.3） |
| 数据库 | MySQL 8.x，测试环境使用 H2 |
| 工具 | Lombok |
| 测试 | Spring Boot Starter Test / JUnit 5 |

> 本机可能安装了其他 Maven 版本（例如 3.8.5），请使用项目内的 `mvnw` / `mvnw.cmd`，不要直接使用系统 `mvn`，以确保构建使用 3.8.1。

## 目录结构

```text
backend/
├── .mvn/wrapper/
│   ├── maven-wrapper.jar
│   └── maven-wrapper.properties   # 固定 Maven 3.8.1 下载地址
├── src/
│   ├── main/
│   │   ├── java/com/codeseek/
│   │   │   ├── SearchEngineApplication.java          # 启动类
│   │   │   ├── gitee/
│   │   │   │   ├── api/GiteeApi.java                 # 面向业务层的接口契约
│   │   │   │   ├── client/GiteeApiClient.java        # RestClient 实现、分页与字段映射
│   │   │   │   ├── config/GiteeApiConfiguration.java # 客户端装配
│   │   │   │   ├── config/GiteeApiProperties.java    # gitee.api 配置
│   │   │   │   ├── exception/GiteeApiException.java  # 统一异常
│   │   │   │   └── model/GiteeRepository.java        # Gitee 仓库统一模型
│   │   │   └── repository/
│   │   │       ├── entity/Repository.java            # 仓库表实体
│   │   │       └── mapper/RepositoryMapper.java      # 仓库表 Mapper 接口
│   │   └── resources/
│   │       ├── mapper/RepositoryMapper.xml           # 仓库表 Mapper SQL
│   │       ├── db/schema.sql                         # MySQL 建表脚本
│   │       └── application.yml
│   └── test/
│       ├── java/com/codeseek/
│       │   ├── SearchEngineApplicationTests.java     # Gitee 实际接口调试用例
│       │   ├── gitee/client/GiteeApiClientTest.java  # Gitee 客户端单元测试
│       │   └── repository/mapper/RepositoryMapperTest.java
│       └── resources/
│           ├── application.yml                       # H2 测试数据源
│           └── db/repository-h2.sql                  # H2 测试表结构
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

## 环境要求

- JDK 17
- 可访问 Maven 仓库（项目当前使用阿里云镜像配置，实际以本机 Maven settings 为准）
- 首次执行 Wrapper 时会自动下载 Maven 3.8.1 到用户目录的 `~/.m2/wrapper/dists`

确认版本：

```powershell
# Windows
.\mvnw.cmd -v

# macOS / Linux
./mvnw -v
```

应看到：

```text
Apache Maven 3.8.1
Java version: 17.x
```

## 启动与构建

```powershell
# 进入后端目录
cd backend

# 启动开发服务（默认 8080 端口）
.\mvnw.cmd spring-boot:run

# 运行测试
.\mvnw.cmd test

# 打包可执行 jar
.\mvnw.cmd clean package
```

macOS / Linux 将 `.\mvnw.cmd` 替换为 `./mvnw`。

启动后可访问 `http://localhost:8080`。当前没有 Controller，因此根路径返回 **404** 属于正常现象；后续添加接口后即可访问。

## 配置

`src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: code-repository-search-engine
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://127.0.0.1:3306/search_engine?...}
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath*:mapper/**/*.xml

gitee:
  api:
    base-url: https://gitee.com/api/v5
    access-token: ${GITEE_ACCESS_TOKEN:}
    page-size: 100
    max-pages: 1000
    connect-timeout: 5s
    read-timeout: 30s
```

数据库连接参数可通过环境变量覆盖：

```powershell
$env:MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/search_engine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "你的密码"
```

## 仓库表实体与 Mapper

### 建表脚本

建表脚本位于 [schema.sql](src/main/resources/db/schema.sql)。原始 DDL 的索引定义写在了字段列表内部，直接执行会报语法错误；脚本中已修正为合法的 `INDEX` 声明，并保留原有字段名、索引名和 `AUTO_INCREMENT=10000001`。

```bash
mysql -u root -p < backend/src/main/resources/db/schema.sql
```

### 实体

[Repository.java](src/main/java/com/codeseek/repository/entity/Repository.java) 对应 `repository` 表，字段为：

| Java 字段 | 数据库列 | 说明 |
| --- | --- | --- |
| `id` | `id` | 自增主键 |
| `fullName` | `fullName` | 仓库全名 |
| `htmlUrl` | `htmlUrl` | 仓库网页地址 |
| `readme` | `readme` | README 文本 |
| `repositoryId` | `repositoryId` | Gitee 仓库 ID |

实体使用 Lombok 的 `@Data`、`@NoArgsConstructor`、`@AllArgsConstructor` 生成访问方法。

### Mapper

[RepositoryMapper.java](src/main/java/com/codeseek/repository/mapper/RepositoryMapper.java) 提供以下操作，SQL 位于 [RepositoryMapper.xml](src/main/resources/mapper/RepositoryMapper.xml)：

| 方法 | 说明 |
| --- | --- |
| `insert` | 新增仓库并回填自增主键 |
| `batchInsert` | 批量新增仓库 |
| `selectById` | 按数据库主键查询 |
| `selectByRepositoryId` | 按 Gitee 仓库 ID 查询，返回列表 |
| `selectByFullName` | 按仓库全名查询，返回列表 |
| `selectAll` | 查询全部仓库 |
| `selectPage` | 按 offset/limit 分页查询 |
| `countAll` | 统计仓库总数 |
| `update` | 按主键更新全部字段 |
| `updateReadme` | 单独更新 README |
| `deleteById` | 按主键删除 |

由于 `repositoryId` 和 `fullName` 在原始表结构中都是普通索引而非唯一索引，对应查询方法返回 `List<Repository>`，避免重复数据导致 `TooManyResultsException`。

## Gitee API 客户端

### 设计

- `GiteeApi` 是对外接口，业务层只依赖该接口，不直接依赖 HTTP 实现。
- `GiteeApiClient` 使用 Spring 6.1 的 `RestClient` 实现，负责请求、分页、错误转换和字段映射。
- `GiteeRepository` 只保留四个字段：`fullName`、`htmlUrl`、`readme`、`repositoryId`。
- `GiteeApiConfiguration` 把 `GiteeApiProperties` 和 `GiteeApiClient` 装配为 Spring Bean。

### 接口方法

| 方法 | Gitee 接口 | 分页 | 说明 |
| --- | --- | --- | --- |
| `searchUserNames(String keyword)` | `GET /v5/search/users` | 是 | 返回用户名（login）列表，可直接用于查询用户仓库 |
| `listUserRepositories(String username)` | `GET /v5/users/{username}/repos` | 是 | 返回用户仓库，不读取 README |
| `listOrganizationRepositories(String organization)` | `GET /v5/orgs/{org}/repos` | 是 | 返回组织公开仓库，不读取 README |
| `getRepositoryReadme(String repositoryFullName)` | `GET /v5/repos/{owner}/{repo}/readme` | 单仓库 | 入参为 `owner/repo`，返回单元素仓库列表；没有 README 时返回空列表 |

### 关键实现规则

- 每页默认 100 条，Gitee 单页上限为 100，配置超过 100 会按 100 处理。
- 自动翻页直到当前页返回数量小于 `perPage`，调用方拿到的就是全量结果。
- `max-pages` 用于防止接口异常导致无限翻页；超过上限会抛出 `GiteeApiException`，不会静默截断。
- 用户仓库固定按 `full_name asc` 排序，降低翻页期间数据顺序变化带来的漏取风险。
- 组织仓库固定请求 `type=public`，避免把私有仓库带入搜索索引。
- README 内容按 Base64 MIME 解码为 UTF-8 文本；仓库没有 README 时按空列表处理。
- Gitee README 响应不包含仓库 ID 和网页地址，因此实现中会补查一次仓库详情接口，确保四个字段完整。
- 所有 Gitee 错误统一包装为 `GiteeApiException`，可通过 `getStatusCode()` 和 `getResponseBody()` 排查限流、权限等问题。

### 访问令牌

匿名调用会受 Gitee 较低的接口频率限制。建议配置个人访问令牌：

```powershell
$env:GITEE_ACCESS_TOKEN = "你的 Gitee Token"
.\mvnw.cmd spring-boot:run
```

也可以通过 `application.yml` 的 `gitee.api.access-token` 直接配置，但不要把真实 Token 提交到仓库。

### 使用示例

```java
@Service
public class GiteeRepositorySyncService {

    private final GiteeApi giteeApi;

    public GiteeRepositorySyncService(GiteeApi giteeApi) {
        this.giteeApi = giteeApi;
    }

    public void sync() {
        List<String> usernames = giteeApi.searchUserNames("权限管理");
        for (String username : usernames) {
            List<GiteeRepository> repositories = giteeApi.listUserRepositories(username);
            // 在这里写入搜索索引或数据库。
        }
    }
}
```

## 测试说明

测试包含：

- 8 个 Gitee API 客户端单元测试，覆盖分页、字段映射、私有仓库过滤、README 解码、404 和 HTTP 错误处理。
- 2 个基于 H2 的 `RepositoryMapper` 集成测试，覆盖新增、批量新增、查询、分页、更新和删除。
- `SearchEngineApplicationTests` 中 4 个调用 Gitee 实际接口的调试用例，运行全量测试时需要能访问 Gitee；如只验证本地代码，可单独执行 `GiteeApiClientTest` 和 `RepositoryMapperTest`。

若运行环境限制 JVM 自附加（例如部分受限沙箱），Mockito 可能初始化失败，可追加：

```powershell
.\mvnw.cmd test "-DargLine=-Djdk.attach.allowAttachSelf=true"
```

普通本地开发环境一般不需要该参数。

## 后续建议

1. 基于 `GiteeApi` 和 `RepositoryMapper` 增加仓库同步服务：拉取用户/组织仓库、补 README、落库并去重。
2. 按 PRD 增加对外搜索接口、分页参数与统一响应体。
3. 接入搜索索引（Elasticsearch / OpenSearch）与增量同步任务。
4. 增加统一异常处理、参数校验、接口文档（SpringDoc）和日志规范。
5. 将 `ui/` 静态原型接入真实接口，或部署到 `src/main/resources/static`。
6. 根据团队规范补充 Checkstyle、Spotless、CI 和代码覆盖率配置。
