# Code Repository Search Engine — Backend

代码仓库搜索引擎的后端项目，使用 Spring Boot 3.3.3 构建。已实现 Gitee 用户/组织仓库同步、仓库表实体与 MyBatis Mapper、结巴分词倒排索引，以及倒排索引 + AI 语义向量的混合检索，并提供 `GET /search` HTTP 接口。

## 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| Spring Boot | 3.3.3 |
| Java | 17（Spring Boot 3 最低要求） |
| Maven | 3.8.1（通过 Maven Wrapper 固定） |
| Web | Spring Boot Starter Web |
| 持久层 | MyBatis-Plus 3.5.5（mybatis-plus-spring-boot3-starter） |
| 数据库 | MySQL 8.x，开发与测试统一连接实际数据库 |
| 分词 | jieba-analysis 1.0.2，搜索引擎模式 |
| 语义检索 | Python 3.14 + text2vec-base-chinese + FAISS（独立 HTTP 服务） |
| HTTP 客户端 | Spring RestClient，用于调用 Python 语义服务 |
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
│   │   │   ├── controller/
│   │   │   │   ├── SearchController.java              # GET /search
│   │   │   │   └── SearchRequest.java                 # 查询请求体
│   │   │   ├── config/RequestLoggingFilter.java       # HTTP 请求运行日志
│   │   │   ├── semantic/                              # AI 语义检索客户端
│   │   │   │   ├── SemanticSearchService.java
│   │   │   │   ├── PythonSemanticSearchService.java
│   │   │   │   ├── SemanticSearchException.java
│   │   │   │   └── model/                             # Python 响应模型
│   │   │   ├── gitee/
│   │   │   │   ├── api/GiteeApi.java                 # 面向业务层的接口契约
│   │   │   │   ├── client/GiteeApiClient.java        # RestClient 实现、分页与字段映射
│   │   │   │   ├── config/GiteeApiConfiguration.java # 客户端装配
│   │   │   │   ├── config/GiteeApiProperties.java    # gitee.api 配置
│   │   │   │   ├── exception/GiteeApiException.java  # 统一异常
│   │   │   │   └── model/GiteeRepository.java        # Gitee 仓库统一模型
│   │   │   ├── repository/
│   │   │   │   ├── entity/Repository.java             # 仓库表实体
│   │   │   │   ├── mapper/RepositoryMapper.java       # 仓库表 Mapper 接口
│   │   │   │   └── service/
│   │   │   │       ├── RepositorySyncService.java     # 仓库同步接口
│   │   │   │       └── RepositorySyncServiceImpl.java # 单线程同步实现
│   │   │   └── index/                                 # 倒排索引模块
│   │   │       ├── builder/RepositoryIndexBuilder.java
│   │   │       ├── config/RepositoryIndexProperties.java
│   │   │       ├── model/                             # 文档、倒排列表、检索结果
│   │   │       ├── search/MultiWayIndexSearcher.java
│   │   │       ├── segment/JiebaTextSegmenter.java
│   │   │       ├── segment/StopWordFilter.java
│   │   │       ├── service/RepositoryIndexService.java
│   │   │       └── store/IndexFileStore.java
│   │   └── resources/
│   │       ├── mapper/RepositoryMapper.xml           # 仓库表 Mapper SQL
│   │       ├── db/schema.sql                         # MySQL 建表脚本
│   │       ├── logback-spring.xml                    # 控制台与滚动文件日志
│   │       └── application.yml
│   └── test/
│       ├── java/com/codeseek/
│       │   ├── SearchEngineApplicationTests.java     # Gitee 实际接口调试用例
│       │   ├── controller/SearchControllerTest.java # 搜索接口测试
│       │   ├── gitee/client/GiteeApiClientTest.java  # Gitee 客户端单元测试
│       │   ├── repository/mapper/RepositoryMapperTest.java
│       │   ├── repository/service/RepositorySyncServiceImplTest.java
│       │   ├── index/
│       │   │   ├── RepositoryIndexServiceTest.java
│       │   │   ├── segment/JiebaTextSegmenterTest.java
│       │   │   └── service/RepositoryIndexServiceImplTest.java
│       │   └── semantic/PythonSemanticSearchServiceTest.java
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

## 环境要求

- JDK 17
- 可访问 Maven 仓库（项目当前使用阿里云镜像配置，实际以本机 Maven settings 为准）
- 首次执行 Wrapper 时会自动下载 Maven 3.8.1 到用户目录的 `~/.m2/wrapper/dists`
- MySQL 8.x，数据库 `search_engine`
- Python 3.14 语义服务（默认 `http://127.0.0.1:8080`），用于提供 AI 语义召回

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

# 数据库账号密码
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"

# 启动开发服务（默认 80 端口）
.\mvnw.cmd spring-boot:run

# 或打包后直接运行
.\mvnw.cmd -DskipTests package
& "$env:JAVA_HOME\bin\java.exe" -jar target\code-repository-search-engine-0.0.1-SNAPSHOT.jar

# 运行测试
.\mvnw.cmd test
```

macOS / Linux 将 `.\mvnw.cmd` 替换为 `./mvnw`。

启动后可访问 `http://localhost:80`，搜索接口为 `GET /search`。如果 `spring-boot:run` 在含中文路径的工作区出现主类加载问题，优先使用打包后的 jar 启动。Windows 下监听 80 端口可能需要管理员权限。

## 配置

`src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: code-repository-search-engine
  datasource:
    url: "jdbc:mysql://127.0.0.1:3306/search_engine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
    username: ${MYSQL_USERNAME}
    password: ${MYSQL_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

server:
  port: 80

mybatis:
  mapper-locations: classpath*:mapper/**/*.xml

repository:
  index:
    path: ${REPOSITORY_INDEX_PATH:./data/repository-index.bin}
  semantic-search:
    base-url: ${SEMANTIC_SEARCH_BASE_URL:http://127.0.0.1:8080}

gitee:
  api:
    base-url: https://gitee.com/api/v5
    access-token: ${GITEE_ACCESS_TOKEN:}
    page-size: 100
    max-pages: 1000
    connect-timeout: 5s
    read-timeout: 30s

logging:
  file:
    path: ${LOG_PATH:./logs}
  level:
    root: INFO
    com.codeseek: INFO
```

数据库账号密码通过环境变量提供：

```powershell
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "你的密码"
```

`MYSQL_URL` 当前未作为配置占位符使用；如需切换数据库地址，请修改 `application.yml` 中的 `spring.datasource.url`。

## 运行日志

项目使用 [logback-spring.xml](src/main/resources/log/logback-spring.xml) 同时输出控制台日志和滚动文件日志。

默认日志文件：

```text
backend/logs/backend.log
```

滚动规则：

- 单文件最大 50MB；
- 按天和文件大小切分；
- 历史文件保留 30 天；
- 日志总量上限 2GB；
- 历史文件使用 `.log.gz` 压缩。

日志目录可通过 [application.yml](src/main/resources/application.yml) 或环境变量修改：

```powershell
$env:LOG_PATH = "C:\JAVA\logs\code-repository-search-engine"
```

运行日志包含：

- HTTP 请求方法、路径、查询参数、响应状态、耗时和客户端 IP；
- 搜索关键词、倒排候选数、语义候选数、交集返回数和搜索耗时；
- Python 语义服务调用结果、失败原因和降级日志；
- 倒排索引加载、重建和启动信息。

请求过滤器 [RequestLoggingFilter.java](src/main/java/com/codeseek/config/RequestLoggingFilter.java) 不记录请求体，避免 README 等大文本进入日志；查询参数超过 500 个字符会被截断。

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

[RepositoryMapper.java](src/main/java/com/codeseek/repository/mapper/RepositoryMapper.java) 继承 MyBatis-Plus 的 `BaseMapper<Repository>`，并保留自定义 SQL 方法。SQL 位于 [RepositoryMapper.xml](src/main/resources/mapper/RepositoryMapper.xml)：

| 方法 | 说明 |
| --- | --- |
| `insert` | 新增仓库并回填自增主键 |
| `batchInsert` | 批量新增仓库 |
| `selectById` | 按数据库主键查询 |
| `selectByIds` | 按主键集合批量查询，默认按主键升序 |
| `selectByIdsInOrder` | 按主键集合批量查询，返回顺序与入参 `ids` 一致 |
| `selectByRepositoryId` | 按 Gitee 仓库 ID 查询，返回列表 |
| `selectByFullName` | 按仓库全名查询，返回列表 |
| `selectAll` | 查询全部仓库 |
| `selectPage` | 按 offset/limit 分页查询 |
| `countAll` | 统计仓库总数 |
| `update` | 按主键更新全部字段 |
| `updateReadme` | 单独更新 README |
| `deleteById` | 按主键删除 |

由于 `repositoryId` 和 `fullName` 在原始表结构中都是普通索引而非唯一索引，对应查询方法返回 `List<Repository>`，避免重复数据导致 `TooManyResultsException`。

`selectByIdsInOrder` 使用 MySQL `FIELD(id, ...)` 排序，用于把倒排索引/语义检索返回的 ID 顺序原样映射到仓库列表。空 `ids` 返回空列表。

## 仓库同步服务

[RepositorySyncService.java](src/main/java/com/codeseek/repository/service/RepositorySyncService.java) 只提供一个方法：

```java
List<Repository> syncRepositories(String username);
```

实现类 [RepositorySyncServiceImpl.java](src/main/java/com/codeseek/repository/service/RepositorySyncServiceImpl.java) 的执行流程：

1. 调用 `giteeApi.listUserRepositories(username)` 查询用户仓库。
2. 如果用户仓库为空，则把 `username` 当作组织名，调用 `giteeApi.listOrganizationRepositories(username)`。
3. 使用普通 `for` 循环逐个仓库处理，保持单线程顺序执行。
4. 从仓库全名中截取仓库名，拼接 `username/repoName` 调用 `getRepositoryReadme`。
5. 将 README 文本填充到 `Repository.readme`，其余字段来自仓库列表结果。
6. 调用 `repositoryMapper.batchInsert(repositories)` 一次性批量保存。
7. 返回本次查询并保存的完整仓库列表；没有查询到仓库时返回空列表。

使用示例：

```java
@Service
public class RepositoryImportJob {

    private final RepositorySyncService repositorySyncService;

    public RepositoryImportJob(RepositorySyncService repositorySyncService) {
        this.repositorySyncService = repositorySyncService;
    }

    public void execute() {
        List<Repository> repositories = repositorySyncService.syncRepositories("whisper-void");
        System.out.println("同步仓库数量：" + repositories.size());
    }
}
```

> 当前实现按需求使用单线程和一次性批量插入。仓库数量特别大时，后续可以将批量插入拆分为固定大小的批次，并配合事务控制。

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

## 倒排索引

### 构建

[RepositoryIndexService.java](src/main/java/com/codeseek/index/service/RepositoryIndexService.java) 提供索引构建和检索能力。

构建流程：

1. 从 `repository` 表读取全部仓库。
2. 以 `repository.id` 作为文档 ID，构建 `id -> RepositoryDocument` 文档列表。
3. 对 README 做文本归一化：去掉 HTML 标签、标点、符号等特殊字符，只保留中文、字母和数字，并统一小写。
4. 使用 `JiebaSegmenter.SegMode.SEARCH` 搜索引擎模式分词。
5. 在分词完成后过滤中英文停用词（英文如 the、and、for、this；中文如 的、了、一个、因为、进行），避免虚词进入倒排索引。
6. 统计每个词在每个文档中的词频，生成 `词项 -> [(文档 ID, 词频)]` 倒排表。
7. 倒排表中的文档 ID 升序排列，供查询时多路归并使用。
8. 保存为二进制索引文件，并替换内存中的索引快照。

调用示例：

```java
@Autowired
private RepositoryIndexService repositoryIndexService;

IndexBuildResult result = repositoryIndexService.rebuildIndex();
List<RepositorySearchResult> results = repositoryIndexService.search("权限管理", 20);
```

### 检索与多路归并

查询词同样先执行特殊字符清洗，再使用结巴搜索引擎模式分词，并过滤中英文停用词。
[MultiWayIndexSearcher.java](src/main/java/com/codeseek/index/search/MultiWayIndexSearcher.java) 为每个查询词建立一路有序倒排列表游标，使用小根堆按文档 ID 进行多路归并：

- 同一个文档 ID 的多个查询词词频一次性合并；
- 以词频总和作为主要排序依据；
- 词频相同时，命中词数多的优先；
- 仍相同时按文档 ID 升序，保证结果稳定。

倒排层采用“命中任意查询词即可返回”的策略，适合搜索召回；后续如需严格 AND 或 BM25 打分，可以在归并层增加过滤和权重。

混合检索最终要求同时命中倒排结果和语义结果，交集之外的仓库不会返回。

### 混合检索

[RepositoryIndexServiceImpl.java](src/main/java/com/codeseek/index/service/RepositoryIndexServiceImpl.java) 的 `search` 方法现在执行混合检索：

1. 倒排索引按词频召回最多 1000 个仓库 ID；
2. 调用本地 Python 向量服务，按 AI 语义相似度召回最多 1000 个仓库 ID；
3. 对两个 ID 列表取交集；
4. 按 AI 语义结果中的顺序返回交集仓库。

Python 服务地址：

```yaml
repository:
  semantic-search:
    base-url: ${SEMANTIC_SEARCH_BASE_URL:http://127.0.0.1:8080}
```

使用前需要先启动 Python 向量服务：

```powershell
cd python
$env:PYTHONNOUSERSITE = "1"
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"
C:\JAVA\Python314\python.exe serve_repository_vectors.py --host 127.0.0.1 --port 8080
```

如果 Python 语义服务暂时不可用，Java 会记录告警并降级为倒排索引结果，避免整个搜索接口不可用。

### 索引文件加载优化

[IndexFileStore.java](src/main/java/com/codeseek/index/store/IndexFileStore.java) 使用自定义紧凑二进制格式，加载时：

- 只做一次顺序读取，避免 JSON / Java 序列化解析；
- 使用 1MB `BufferedInputStream` 减少系统调用；
- 从文件头读取文档数、词项数，并按预期容量预初始化 `HashMap`；
- 倒排项使用 `long[]` 和 `int[]` 原始数组，不为每个文档词频创建对象；
- 写入临时文件后原子替换目标文件，避免加载到不完整索引。

索引文件默认路径：

```yaml
repository:
  index:
    path: ./data/repository-index.bin
```

索引文件属于运行时数据，已加入 `.gitignore`。应用启动时会自动尝试加载已有索引；索引不存在时使用空索引，之后可通过 `rebuildIndex()` 重建。

### IDEA 启动时索引路径问题

IDEA 的 Run Configuration 默认工作目录通常是项目根目录：

```text
code-repository-search-engine/
```

而 `./data/repository-index.bin` 会解析成：

```text
code-repository-search-engine/data/repository-index.bin
```

实际索引文件在：

```text
code-repository-search-engine/backend/data/repository-index.bin
```

因此旧版本会提示「倒排索引文件不存在」。现在已增加兼容逻辑：如果当前目录找不到索引，会继续尝试 `backend/data/repository-index.bin`。

推荐仍然把 IDEA Run Configuration 的 **Working directory** 设置为：

```text
$PROJECT_DIR$/backend
```

也可以通过环境变量显式指定绝对路径：

```powershell
$env:REPOSITORY_INDEX_PATH = "C:\JAVA\项目\基于 AI 工具的代码仓库搜索引擎\code-repository-search-engine\backend\data\repository-index.bin"
```

或在 IDEA 的 Environment variables 中添加同名变量。配置项已支持：

```yaml
repository:
  index:
    path: ${REPOSITORY_INDEX_PATH:./data/repository-index.bin}
```

## 搜索 HTTP 接口

[SearchController.java](src/main/java/com/codeseek/controller/SearchController.java) 提供：

```text
GET /search
```

支持 JSON 请求体：

```http
GET /search
Content-Type: application/json

{"query":"大模型"}
```

也支持查询参数，便于浏览器和简单客户端调试：

```text
GET /search?query=大模型
```

响应为 `Repository` 数组，最多 1000 条：

```json
[
  {
    "id": 10000025,
    "fullName": "rae-gard712/large-model",
    "htmlUrl": "https://gitee.com/rae-gard712/large-model.git",
    "readme": "# largeModel\n...",
    "repositoryId": 50953942
  }
]
```

实现说明：

- 应用启动时 [RepositoryIndexServiceImpl.java](src/main/java/com/codeseek/index/service/RepositoryIndexServiceImpl.java) 的 `@PostConstruct` 会调用 `IndexFileStore.load` 加载 `./data/repository-index.bin`。
- 查询关键词传给 `RepositoryIndexService.search`，由倒排索引和 Python 语义服务分别召回 ID，然后取交集。
- 控制器使用 `RepositoryMapper.selectByIdsInOrder` 一次性批量补齐 `readme`、`repositoryId` 等完整字段，并保持语义 ID 顺序。
- 最终结果按 AI 语义顺序返回，并限制最多 1000 条。
- 查询为空、没有交集或没有匹配结果时返回 `[]`。
- Python 语义服务不可用时，搜索降级为倒排索引结果。
- PowerShell 的 `Invoke-RestMethod` 不支持 GET 请求体，调试时可使用 `?query=` 形式，或使用 curl / Postman 发送 JSON body。

启动示例：

```powershell
cd backend
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"

.\mvnw.cmd -DskipTests package
& "$env:JAVA_HOME\bin\java.exe" -jar target\code-repository-search-engine-0.0.1-SNAPSHOT.jar
```

## 测试说明

数据库相关测试直接连接 [application.yml](src/main/resources/application.yml) 配置的实际 MySQL 数据库。

运行数据库相关测试前：

1. 执行 [schema.sql](src/main/resources/db/schema.sql) 创建数据库和 `repository` 表。
2. 配置正确的 `MYSQL_USERNAME`、`MYSQL_PASSWORD`。
3. 数据库相关测试类使用 `@Transactional`，每个测试方法结束后自动回滚，测试数据不会留在实际数据库中。

测试包含：

- 8 个 Gitee API 客户端单元测试，覆盖分页、字段映射、私有仓库过滤、README 解码、404 和 HTTP 错误处理。
- 3 个基于实际 MySQL 的 `RepositoryMapper` 集成测试，覆盖新增、批量新增、查询、分页、更新、删除和 `selectByIdsInOrder` 顺序。
- 2 个基于实际 MySQL 的 `RepositorySyncServiceImpl` 测试，覆盖组织仓库兜底、README 填充和批量落库。
- 2 个 `RepositoryIndexServiceTest` 测试，覆盖倒排索引重建和检索。
- 6 个分词与中英文停用词测试，覆盖英文/中文停用词过滤、技术词与中文词保留、纯停用词查询。
- 2 个混合检索测试，覆盖倒排与语义结果交集、语义顺序和语义服务不可用时的降级。
- 2 个 Python 语义客户端测试，覆盖响应解析和 HTTP 错误转换。
- 2 个 `SearchControllerTest` 接口测试。
- `SearchEngineApplicationTests` 中 4 个调用 Gitee 实际接口的调试用例，运行全量测试时需要能访问 Gitee；如只验证本地代码，可单独执行 `GiteeApiClientTest`、`RepositoryMapperTest`、`RepositorySyncServiceImplTest` 和倒排索引测试。

若运行环境限制 JVM 自附加（例如部分受限沙箱），Mockito 可能初始化失败，可追加：

```powershell
.\mvnw.cmd test "-DargLine=-Djdk.attach.allowAttachSelf=true"
```

普通本地开发环境一般不需要该参数。

## 后续建议

1. 基于 `RepositorySyncService` 增加批量用户同步、定时任务和重复数据更新策略。
2. 为 `GET /search` 增加分页参数、统一响应体和异常处理。
3. 将本地倒排索引扩展为分布式索引（Elasticsearch / OpenSearch），并增加增量重建能力。
4. 增加统一异常处理、参数校验、接口文档（SpringDoc）和日志规范。
5. 将 `ui/` 静态原型接入真实接口，或部署到 `src/main/resources/static`。
6. 根据团队规范补充 Checkstyle、Spotless、CI 和代码覆盖率配置。
