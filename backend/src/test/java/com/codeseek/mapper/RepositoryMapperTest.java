package com.codeseek.mapper;

import com.codeseek.repository.mapper.RepositoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RepositoryMapperTest {

    @Autowired
    private RepositoryMapper repositoryMapper;

    @Test
    void updateReadme() {
        repositoryMapper.updateReadme(10000027L, "# 代码仓库搜索引擎\n" +
                "\n" +
                "基于 Gitee 仓库 README 的代码仓库搜索引擎。项目将传统倒排索引与 AI 语义向量检索结合：\n" +
                "\n" +
                "- 结巴分词 + 倒排索引，负责关键词词频召回；\n" +
                "- text2vec-base-chinese + FAISS，负责语义相似度召回；\n" +
                "- 两路 ID 取交集，最终按 AI 语义结果顺序返回仓库。\n" +
                "\n" +
                "## 功能\n" +
                "\n" +
                "- 按用户名同步 Gitee 用户仓库，用户没有仓库时回退到组织仓库；\n" +
                "- 自动翻页获取 Gitee 全量仓库，过滤私有仓库；\n" +
                "- 获取仓库 README，并批量写入 MySQL `repository` 表；\n" +
                "- 基于 Jieba `SEARCH` 模式构建倒排索引；\n" +
                "- 查询时执行倒排召回与语义召回的交集检索；\n" +
                "- 提供 `GET /search` HTTP 接口，返回完整仓库数据；\n" +
                "- 支持控制台与滚动文件运行日志，记录 HTTP 请求和混合检索耗时；\n" +
                "- 提供图文仓库卡片、关键词搜索、分页和响应式布局的静态 UI 原型；\n" +
                "- 包含产品需求文档、后端说明和 Python 向量服务说明。\n" +
                "\n" +
                "## 软件架构\n" +
                "\n" +
                "```text\n" +
                "                         ┌──────────────────────────┐\n" +
                "                         │        UI 原型           │\n" +
                "                         │  ui/index.html           │\n" +
                "                         └────────────┬─────────────┘\n" +
                "                                      │ GET /search\n" +
                "                                      ▼\n" +
                "┌─────────────────────────────────────────────────────────────┐\n" +
                "│                    Spring Boot 3.3.3 / Java 17              │\n" +
                "│                                                             │\n" +
                "│  SearchController                                           │\n" +
                "│        │                                                    │\n" +
                "│        ▼                                                    │\n" +
                "│  RepositoryIndexService.search                              │\n" +
                "│        ├── Jieba 分词 → 倒排索引 → 按词频召回 ID 列表        │\n" +
                "│        └── Python 语义服务 → 语义相似度召回 ID 列表           │\n" +
                "│                         │                                   │\n" +
                "│                         ▼                                   │\n" +
                "│              两路 ID 取交集，按语义顺序返回                   │\n" +
                "│                         │                                   │\n" +
                "│                         ▼                                   │\n" +
                "│              RepositoryMapper.selectByIdsInOrder            │\n" +
                "│                         │                                   │\n" +
                "│                         ▼                                   │\n" +
                "│                      MySQL repository                       │\n" +
                "└──────────────────────────┬──────────────────────────────────┘\n" +
                "                           │ HTTP\n" +
                "                           ▼\n" +
                "┌─────────────────────────────────────────────────────────────┐\n" +
                "│              Python 3.14 语义向量服务（8080）                │\n" +
                "│  text2vec-base-chinese → 768 维向量 → FAISS IndexFlatIP      │\n" +
                "└─────────────────────────────────────────────────────────────┘\n" +
                "```\n" +
                "\n" +
                "## 目录结构\n" +
                "\n" +
                "```text\n" +
                "code-repository-search-engine/\n" +
                "├── backend/                  Spring Boot 后端\n" +
                "│   ├── src/main/java/com/codeseek/\n" +
                "│   │   ├── controller/       GET /search\n" +
                "│   │   ├── gitee/            Gitee API 客户端\n" +
                "│   │   ├── repository/       仓库实体、Mapper、同步服务\n" +
                "│   │   ├── index/            倒排索引构建、分词、文件存储、检索\n" +
                "│   │   └── semantic/         Python 语义检索客户端\n" +
                "│   ├── src/main/resources/\n" +
                "│   │   ├── application.yml\n" +
                "│   │   ├── mapper/           MyBatis XML\n" +
                "│   │   └── db/schema.sql     MySQL 建表脚本\n" +
                "│   └── README.md             后端详细文档\n" +
                "├── python/                   文档向量预计算与语义检索服务\n" +
                "│   ├── build_repository_vectors.py\n" +
                "│   ├── serve_repository_vectors.py\n" +
                "│   ├── query_repository_vectors.py\n" +
                "│   └── README.md\n" +
                "├── prd/                      产品需求文档\n" +
                "├── ui/                       静态 HTML/CSS 原型\n" +
                "├── README.en.md              English README\n" +
                "└── README.md\n" +
                "```\n" +
                "\n" +
                "## 环境要求\n" +
                "\n" +
                "| 组件 | 版本/说明 |\n" +
                "|---|---|\n" +
                "| JDK | 17 |\n" +
                "| Maven | 3.8.1（使用项目自带 Wrapper） |\n" +
                "| MySQL | 8.x，数据库 `search_engine` |\n" +
                "| Python | 3.14.0，当前环境 `C:\\JAVA\\Python314\\python.exe` |\n" +
                "| AI 模型 | 本地 `text2vec-base-chinese` |\n" +
                "| 向量索引 | FAISS `IndexFlatIP`，不可用时退化为 numpy 余弦检索 |\n" +
                "\n" +
                "Python 依赖：\n" +
                "\n" +
                "```powershell\n" +
                "cd python\n" +
                "python -m pip install -r requirements.txt\n" +
                "python -m pip install -r requirements-optional.txt\n" +
                "```\n" +
                "\n" +
                "## 数据库\n" +
                "\n" +
                "先执行 [schema.sql](backend/src/main/resources/db/schema.sql) 创建数据库和 `repository` 表。\n" +
                "\n" +
                "数据库账号密码通过环境变量提供，不写入代码：\n" +
                "\n" +
                "```powershell\n" +
                "$env:MYSQL_USERNAME = \"root\"\n" +
                "$env:MYSQL_PASSWORD = \"你的 MySQL root 密码\"\n" +
                "```\n" +
                "\n" +
                "如果需要重建仓库数据，可调用 `RepositorySyncService.syncRepositories(username)`，或通过原有测试和调试入口触发同步。\n" +
                "\n" +
                "## 构建与启动\n" +
                "\n" +
                "### 1. 构建 Python 向量索引\n" +
                "\n" +
                "```powershell\n" +
                "cd python\n" +
                "$env:PYTHONNOUSERSITE = \"1\"\n" +
                "$env:MYSQL_PASSWORD = \"你的 MySQL root 密码\"\n" +
                "C:\\JAVA\\Python314\\python.exe build_repository_vectors.py\n" +
                "```\n" +
                "\n" +
                "默认输出到 `C:\\JAVA\\search_engine`：\n" +
                "\n" +
                "```text\n" +
                "repository_ids.npy\n" +
                "repository_vectors.npy\n" +
                "repository_faiss.index\n" +
                "repository_index_meta.json\n" +
                "```\n" +
                "\n" +
                "### 2. 启动 Python 语义服务\n" +
                "\n" +
                "```powershell\n" +
                "cd python\n" +
                "$env:PYTHONNOUSERSITE = \"1\"\n" +
                "$env:MYSQL_PASSWORD = \"你的 MySQL root 密码\"\n" +
                "C:\\JAVA\\Python314\\python.exe serve_repository_vectors.py --host 127.0.0.1 --port 8080\n" +
                "```\n" +
                "\n" +
                "语义服务接口：\n" +
                "\n" +
                "```text\n" +
                "GET http://127.0.0.1:8080/search?q=大模型&topK=10\n" +
                "GET http://127.0.0.1:8080/health\n" +
                "```\n" +
                "\n" +
                "### 3. 构建并启动 Java 服务\n" +
                "\n" +
                "```powershell\n" +
                "cd backend\n" +
                "$env:MYSQL_USERNAME = \"root\"\n" +
                "$env:MYSQL_PASSWORD = \"你的 MySQL root 密码\"\n" +
                "\n" +
                ".\\mvnw.cmd -DskipTests package\n" +
                "& \"$env:JAVA_HOME\\bin\\java.exe\" -jar target\\code-repository-search-engine-0.0.1-SNAPSHOT.jar\n" +
                "```\n" +
                "\n" +
                "服务端接口默认端口：\n" +
                "\n" +
                "```text\n" +
                "http://127.0.0.1:80\n" +
                "```\n" +
                "\n" +
                "Windows 下监听 80 端口可能需要管理员权限。\n" +
                "\n" +
                "## 搜索接口\n" +
                "\n" +
                "### 请求\n" +
                "\n" +
                "```http\n" +
                "GET /search\n" +
                "Content-Type: application/json\n" +
                "\n" +
                "{\n" +
                "  \"query\": \"大模型\"\n" +
                "}\n" +
                "```\n" +
                "\n" +
                "也支持查询参数：\n" +
                "\n" +
                "```text\n" +
                "GET /search?query=大模型\n" +
                "```\n" +
                "\n" +
                "### 响应\n" +
                "\n" +
                "```json\n" +
                "[\n" +
                "  {\n" +
                "    \"id\": 10000025,\n" +
                "    \"fullName\": \"rae-gard712/large-model\",\n" +
                "    \"htmlUrl\": \"https://gitee.com/rae-gard712/large-model.git\",\n" +
                "    \"readme\": \"# largeModel\\n...\",\n" +
                "    \"repositoryId\": 50953942\n" +
                "  }\n" +
                "]\n" +
                "```\n" +
                "\n" +
                "响应说明：\n" +
                "\n" +
                "- 返回 `Repository` 数组，单次最多 1000 条；\n" +
                "- 倒排索引和语义索引取交集；\n" +
                "- 返回顺序遵循 AI 语义检索结果；\n" +
                "- 查询为空或无交集结果时返回 `[]`；\n" +
                "- Python 语义服务不可用时，Java 会降级为倒排索引结果。\n" +
                "\n" +
                "## 混合检索配置\n" +
                "\n" +
                "[application.yml](backend/src/main/resources/application.yml)：\n" +
                "\n" +
                "```yaml\n" +
                "server:\n" +
                "  port: 80\n" +
                "\n" +
                "repository:\n" +
                "  index:\n" +
                "    path: ${REPOSITORY_INDEX_PATH:./data/repository-index.bin}\n" +
                "  semantic-search:\n" +
                "    base-url: ${SEMANTIC_SEARCH_BASE_URL:http://127.0.0.1:8080}\n" +
                "```\n" +
                "\n" +
                "Python 服务地址可以通过环境变量覆盖：\n" +
                "\n" +
                "```powershell\n" +
                "$env:SEMANTIC_SEARCH_BASE_URL = \"http://127.0.0.1:8080\"\n" +
                "```\n" +
                "\n" +
                "## 运行日志\n" +
                "\n" +
                "后端使用 Logback 同时输出控制台和滚动文件日志，默认位置：\n" +
                "\n" +
                "```text\n" +
                "backend/logs/backend.log\n" +
                "```\n" +
                "\n" +
                "滚动策略：\n" +
                "\n" +
                "- 单文件最大 50MB；\n" +
                "- 按天和文件大小切分；\n" +
                "- 历史文件保留 30 天；\n" +
                "- 日志总量上限 2GB；\n" +
                "- 历史文件使用 `.log.gz` 压缩。\n" +
                "\n" +
                "日志目录可通过环境变量覆盖：\n" +
                "\n" +
                "```powershell\n" +
                "$env:LOG_PATH = \"C:\\JAVA\\logs\\code-repository-search-engine\"\n" +
                "```\n" +
                "\n" +
                "记录内容包括：\n" +
                "\n" +
                "- HTTP 方法、路径、查询参数、响应状态、耗时和客户端 IP；\n" +
                "- 搜索关键词、倒排候选数、语义候选数、交集返回数量和搜索耗时；\n" +
                "- Python 语义服务调用耗时、失败原因和降级日志；\n" +
                "- 倒排索引加载、重建和服务启动信息。\n" +
                "\n" +
                "请求日志不会记录请求体，避免 README 文本进入日志。`logs/` 已加入 `.gitignore`，不会提交到仓库。\n" +
                "\n" +
                "## UI 原型\n" +
                "\n" +
                "直接用浏览器打开：\n" +
                "\n" +
                "```text\n" +
                "ui/index.html\n" +
                "```\n" +
                "\n" +
                "包含首页、搜索结果页、图文仓库卡片、分页和响应式布局。详细说明见 [ui/README.md](ui/README.md)。\n" +
                "\n" +
                "## 测试\n" +
                "\n" +
                "```powershell\n" +
                "cd backend\n" +
                "$env:MYSQL_USERNAME = \"root\"\n" +
                "$env:MYSQL_PASSWORD = \"你的 MySQL root 密码\"\n" +
                ".\\mvnw.cmd test\n" +
                "```\n" +
                "\n" +
                "如果运行环境限制 JVM 自附加：\n" +
                "\n" +
                "```powershell\n" +
                ".\\mvnw.cmd test \"-DargLine=-Djdk.attach.allowAttachSelf=true\"\n" +
                "```\n" +
                "\n" +
                "数据库测试使用真实 MySQL 并通过 `@Transactional` 回滚；Gitee 实际接口测试需要网络。\n" +
                "\n" +
                "## 文档\n" +
                "\n" +
                "- [PRD：代码仓库搜索引擎](prd/代码仓库搜索引擎-PRD-v1.0.md)\n" +
                "- [后端详细说明](backend/README.md)\n" +
                "- [Python 向量服务说明](python/README.md)\n" +
                "- [UI 原型说明](ui/README.md)\n" +
                "\n" +
                "## 注意事项\n" +
                "\n" +
                "- `MYSQL_PASSWORD`、`GITEE_ACCESS_TOKEN` 等敏感信息通过环境变量提供，不要提交到仓库。\n" +
                "- `.idea/` 已加入 `.gitignore`，避免 IDE 运行配置中的本地密码被提交。\n" +
                "- Java 默认端口 80，Python 默认端口 8080，两个服务需要分别启动。\n" +
                "- 倒排索引文件 `backend/data/repository-index.bin` 属于运行时数据，不提交到仓库。\n" +
                "- 仓库向量和 FAISS 索引默认生成到 `C:\\JAVA\\search_engine`，属于本地离线产物。\n" +
                "\n" +
                "## License\n" +
                "\n" +
                "[MIT](LICENSE)\n");
    }
}
