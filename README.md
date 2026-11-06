# 代码仓库搜索引擎

基于 Gitee 仓库 README 的代码仓库搜索引擎。项目将传统倒排索引与 AI 语义向量检索结合：

- 结巴分词 + 倒排索引，负责关键词词频召回；
- text2vec-base-chinese + FAISS，负责语义相似度召回；
- 两路 ID 取交集，最终按 AI 语义结果顺序返回仓库。

## 功能

- 按用户名同步 Gitee 用户仓库，用户没有仓库时回退到组织仓库；
- 自动翻页获取 Gitee 全量仓库，过滤私有仓库；
- 获取仓库 README，并批量写入 MySQL `repository` 表；
- 基于 Jieba `SEARCH` 模式构建倒排索引，分词后过滤中英文停用词；
- 查询时执行倒排召回与语义召回的交集检索；
- 提供 `GET /search` HTTP 接口，返回完整仓库数据；
- 支持控制台与滚动文件运行日志，记录 HTTP 请求和混合检索耗时；
- 提供图文仓库卡片、关键词搜索、分页和响应式布局的静态 UI 原型；
- 包含产品需求文档、后端说明和 Python 向量服务说明。

## 软件架构

```text
                         ┌──────────────────────────┐
                         │        UI 原型           │
                         │  ui/index.html           │
                         └────────────┬─────────────┘
                                      │ GET /search
                                      ▼
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot 3.3.3 / Java 17              │
│                                                             │
│  SearchController                                           │
│        │                                                    │
│        ▼                                                    │
│  RepositoryIndexService.search                              │
│        ├── Jieba 分词 → 倒排索引 → 按词频召回 ID 列表        │
│        └── Python 语义服务 → 语义相似度召回 ID 列表           │
│                         │                                   │
│                         ▼                                   │
│              两路 ID 取交集，按语义顺序返回                   │
│                         │                                   │
│                         ▼                                   │
│              RepositoryMapper.selectByIdsInOrder            │
│                         │                                   │
│                         ▼                                   │
│                      MySQL repository                       │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Python 3.14 语义向量服务（8080）                │
│  text2vec-base-chinese → 768 维向量 → FAISS IndexFlatIP      │
└─────────────────────────────────────────────────────────────┘
```

## 目录结构

```text
code-repository-search-engine/
├── backend/                  Spring Boot 后端
│   ├── src/main/java/com/codeseek/
│   │   ├── controller/       GET /search
│   │   ├── gitee/            Gitee API 客户端
│   │   ├── repository/       仓库实体、Mapper、同步服务
│   │   ├── index/            倒排索引构建、分词、文件存储、检索
│   │   └── semantic/         Python 语义检索客户端
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── mapper/           MyBatis XML
│   │   └── db/schema.sql     MySQL 建表脚本
│   └── README.md             后端详细文档
├── python/                   文档向量预计算与语义检索服务
│   ├── build_repository_vectors.py
│   ├── serve_repository_vectors.py
│   ├── query_repository_vectors.py
│   └── README.md
├── prd/                      产品需求文档
├── ui/                       静态 HTML/CSS 原型
├── README.en.md              English README
└── README.md
```

## 环境要求

| 组件 | 版本/说明 |
|---|---|
| JDK | 17 |
| Maven | 3.8.1（使用项目自带 Wrapper） |
| MySQL | 8.x，数据库 `search_engine` |
| Python | 3.14.0，当前环境 `C:\JAVA\Python314\python.exe` |
| AI 模型 | 本地 `text2vec-base-chinese` |
| 向量索引 | FAISS `IndexFlatIP`，不可用时退化为 numpy 余弦检索 |

Python 依赖：

```powershell
cd python
python -m pip install -r requirements.txt
python -m pip install -r requirements-optional.txt
```

## 数据库

先执行 [schema.sql](backend/src/main/resources/db/schema.sql) 创建数据库和 `repository` 表。

数据库账号密码通过环境变量提供，不写入代码：

```powershell
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"
```

如果需要重建仓库数据，可调用 `RepositorySyncService.syncRepositories(username)`，或通过原有测试和调试入口触发同步。

## 构建与启动

### 1. 构建 Python 向量索引

```powershell
cd python
$env:PYTHONNOUSERSITE = "1"
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"
C:\JAVA\Python314\python.exe build_repository_vectors.py
```

默认输出到 `C:\JAVA\search_engine`：

```text
repository_ids.npy
repository_vectors.npy
repository_faiss.index
repository_index_meta.json
```

### 2. 启动 Python 语义服务

```powershell
cd python
$env:PYTHONNOUSERSITE = "1"
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"
C:\JAVA\Python314\python.exe serve_repository_vectors.py --host 127.0.0.1 --port 8080
```

语义服务接口：

```text
GET http://127.0.0.1:8080/search?q=大模型&topK=10
GET http://127.0.0.1:8080/health
```

### 3. 构建并启动 Java 服务

```powershell
cd backend
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"

.\mvnw.cmd -DskipTests package
& "$env:JAVA_HOME\bin\java.exe" -jar target\code-repository-search-engine-0.0.1-SNAPSHOT.jar
```

服务端接口默认端口：

```text
http://127.0.0.1:80
```

Windows 下监听 80 端口可能需要管理员权限。

## 搜索接口

### 请求

```http
GET /search
Content-Type: application/json

{
  "query": "大模型"
}
```

也支持查询参数：

```text
GET /search?query=大模型
```

### 响应

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

响应说明：

- 返回 `Repository` 数组，单次最多 1000 条；
- 倒排索引和语义索引取交集；
- 返回顺序遵循 AI 语义检索结果；
- 查询为空或无交集结果时返回 `[]`；
- Python 语义服务不可用时，Java 会降级为倒排索引结果。

## 混合检索配置

[application.yml](backend/src/main/resources/application.yml)：

```yaml
server:
  port: 80

repository:
  index:
    path: ${REPOSITORY_INDEX_PATH:./data/repository-index.bin}
  semantic-search:
    base-url: ${SEMANTIC_SEARCH_BASE_URL:http://127.0.0.1:8080}
```

Python 服务地址可以通过环境变量覆盖：

```powershell
$env:SEMANTIC_SEARCH_BASE_URL = "http://127.0.0.1:8080"
```

## 运行日志

后端使用 Logback 同时输出控制台和滚动文件日志，默认位置：

```text
backend/logs/backend.log
```

滚动策略：

- 单文件最大 50MB；
- 按天和文件大小切分；
- 历史文件保留 30 天；
- 日志总量上限 2GB；
- 历史文件使用 `.log.gz` 压缩。

日志目录可通过环境变量覆盖：

```powershell
$env:LOG_PATH = "C:\JAVA\logs\code-repository-search-engine"
```

记录内容包括：

- HTTP 方法、路径、查询参数、响应状态、耗时和客户端 IP；
- 搜索关键词、倒排候选数、语义候选数、交集返回数量和搜索耗时；
- Python 语义服务调用耗时、失败原因和降级日志；
- 倒排索引加载、重建和服务启动信息。

请求日志不会记录请求体，避免 README 文本进入日志。`logs/` 已加入 `.gitignore`，不会提交到仓库。

## UI 原型

直接用浏览器打开：

```text
ui/index.html
```

包含首页、搜索结果页、图文仓库卡片、分页和响应式布局。详细说明见 [ui/README.md](ui/README.md)。

## 测试

```powershell
cd backend
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"
.\mvnw.cmd test
```

如果运行环境限制 JVM 自附加：

```powershell
.\mvnw.cmd test "-DargLine=-Djdk.attach.allowAttachSelf=true"
```

数据库测试使用真实 MySQL 并通过 `@Transactional` 回滚；Gitee 实际接口测试需要网络。

## 文档

- [PRD：代码仓库搜索引擎](prd/代码仓库搜索引擎-PRD-v1.0.md)
- [后端详细说明](backend/README.md)
- [Python 向量服务说明](python/README.md)
- [UI 原型说明](ui/README.md)

## 注意事项

- `MYSQL_PASSWORD`、`GITEE_ACCESS_TOKEN` 等敏感信息通过环境变量提供，不要提交到仓库。
- `.idea/` 已加入 `.gitignore`，避免 IDE 运行配置中的本地密码被提交。
- Java 默认端口 80，Python 默认端口 8080，两个服务需要分别启动。
- 中英文停用词维护在 `C:\JAVA\search_engine\stopwords.txt`，修改后需要重启并重建倒排索引。
- 倒排索引文件 `backend/data/repository-index.bin` 属于运行时数据，不提交到仓库。
- 仓库向量和 FAISS 索引默认生成到 `C:\JAVA\search_engine`，属于本地离线产物。

## License

[MIT](LICENSE)
