# Code Repository Search Engine — Backend

代码仓库搜索引擎的后端项目，已搭好 Spring Boot 基础结构，并封装了 Gitee 用户、仓库和 README 查询接口；暂不包含业务 Controller、数据库和前端联调代码。

## 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| Spring Boot | 3.3.3 |
| Java | 17（Spring Boot 3 最低要求） |
| Maven | 3.8.1（通过 Maven Wrapper 固定） |
| Web | Spring Boot Starter Web |
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
│   │   │   ├── CodeRepositorySearchEngineApplication.java
│   │   │   └── gitee/
│   │   │       ├── api/GiteeApi.java                 # 面向业务层的接口契约
│   │   │       ├── client/GiteeApiClient.java        # RestClient 实现、分页与字段映射
│   │   │       ├── config/GiteeApiConfiguration.java # 客户端装配
│   │   │       ├── config/GiteeApiProperties.java    # gitee.api 配置
│   │   │       ├── exception/GiteeApiException.java  # 统一异常
│   │   │       └── model/GiteeRepository.java        # 仓库统一模型
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/codeseek/
│           ├── CodeRepositorySearchEngineApplicationTests.java
│           └── gitee/client/GiteeApiClientTest.java
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

server:
  port: 8080

gitee:
  api:
    base-url: https://gitee.com/api/v5
    access-token: ${GITEE_ACCESS_TOKEN:}
    page-size: 100
    max-pages: 1000
    connect-timeout: 5s
    read-timeout: 30s
```

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

测试包含 Spring 上下文加载和 8 个 Gitee API 客户端测试，覆盖分页、字段映射、私有仓库过滤、README 解码、404 和 HTTP 错误处理。若运行环境限制 JVM 自附加（例如部分受限沙箱），Mockito 可能初始化失败，可追加：

```powershell
.\mvnw.cmd test "-DargLine=-Djdk.attach.allowAttachSelf=true"
```

普通本地开发环境一般不需要该参数。

## 后续建议

1. 基于 `GiteeApi` 增加仓库同步服务、数据去重和失败重试。
2. 按 PRD 增加对外搜索接口、分页参数与统一响应体。
3. 接入搜索索引（Elasticsearch / OpenSearch）与增量同步任务。
4. 增加统一异常处理、参数校验、接口文档（SpringDoc）和日志规范。
5. 将 `ui/` 静态原型接入真实接口，或部署到 `src/main/resources/static`。
6. 根据团队规范补充 Checkstyle、Spotless、CI 和代码覆盖率配置。
