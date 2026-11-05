# 仓库 README 向量索引

使用本地 `text2vec-base-chinese` 模型，预计算 MySQL `repository` 表 README 字段的文档向量，并保存向量和索引文件，供后续语义检索使用。

运行环境：**Python 3.14.0**。当前机器使用独立环境 `C:\JAVA\Python314\python.exe`，依赖已安装完成。

## 目录与输出

```text
python/
├── build_repository_vectors.py    # 读取 MySQL、生成向量并构建索引
├── query_repository_vectors.py    # 命令行加载索引执行相似度查询
├── serve_repository_vectors.py    # 本地 HTTP 检索服务（默认 8080）
├── embedding_utils.py             # 模型加载、文本清洗、mean pooling、编码
├── requirements.txt
├── requirements-optional.txt    # 可选安装 faiss-cpu
└── README.md
```

默认输出到 `C:\JAVA\search_engine`：

```text
repository_ids.npy           # 文档 ID，与向量行一一对应
repository_vectors.npy       # float32 归一化文档向量矩阵
repository_faiss.index       # 安装 faiss 时生成的 IndexFlatIP 索引
repository_index_meta.json   # 模型、维度、数量、文件清单等元数据
```

## 模型

默认模型目录：

```text
C:\Users\27271\.cache\modelscope\models\thomas--text2vec-base-chinese
```

脚本会自动识别 ModelScope 的 `snapshots/master` 子目录。该模型是 BERT 结构，本地快照没有 sentence-transformers 的 `modules.json`，因此采用：

- 取最后一层 `last_hidden_state`
- 按 `attention_mask` 做 mean pooling
- L2 归一化

归一化后使用内积等价于余弦相似度。

## 环境

当前机器使用稳定版 Python 3.14：

```powershell
C:\JAVA\Python314\python.exe --version
# Python 3.14.0
```

> 不要直接使用系统 `python` 命令，当前 PATH 中的 `python` 是 3.14.0a1 alpha，与 PyTorch cp314 扩展不兼容。请显式使用 `C:\JAVA\Python314\python.exe`。

当前环境已安装：

```text
torch 2.15.0+cpu
transformers 4.57.6
faiss 1.15.1
numpy 2.5.4
PyMySQL 1.2.3
```

如需在其他 Python 3.14 环境重建依赖：

```powershell
cd python
python -m pip install -r requirements.txt
```

如需 FAISS 索引：

```powershell
python -m pip install -r requirements-optional.txt
```

> 如果没有安装 faiss，脚本仍会保存归一化向量矩阵，并以 numpy 余弦矩阵作为索引；查询脚本会自动识别 `index_type`。

## 数据库配置

脚本只查询：

```sql
SELECT id, readme
FROM repository
WHERE readme IS NOT NULL
  AND CHAR_LENGTH(TRIM(readme)) > 0
ORDER BY id;
```

数据库密码不写入代码，通过环境变量提供：

```powershell
$env:MYSQL_HOST = "127.0.0.1"
$env:MYSQL_PORT = "3306"
$env:MYSQL_USER = "root"
$env:MYSQL_PASSWORD = "你的数据库密码"
$env:MYSQL_DATABASE = "search_engine"
```

## 构建向量索引

```powershell
cd python
$env:PYTHONNOUSERSITE = "1"
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"
C:\JAVA\Python314\python.exe build_repository_vectors.py
```

## 常见错误：1045 Access denied

如果出现：

```text
pymysql.err.OperationalError: (1045, "Access denied for user 'root'@'localhost' (using password: YES)")
```

说明 MySQL 拒绝了连接，通常是 `MYSQL_PASSWORD` 没设置、设置到了另一个 PowerShell 窗口，或者值包含了多余引号/空格。

每个 PowerShell 窗口的 `$env:` 变量互不继承，请在当前窗口重新设置：

```powershell
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"

# 只检查是否设置，不打印密码内容
if ([string]::IsNullOrEmpty($env:MYSQL_PASSWORD)) {
    "MYSQL_PASSWORD 未设置"
} else {
    "MYSQL_PASSWORD 已设置，长度=$($env:MYSQL_PASSWORD.Length)"
}

C:\JAVA\Python314\python.exe build_repository_vectors.py
```

如果使用了系统级/用户级环境变量且密码已过期，建议先清除当前窗口变量再重新设置：

```powershell
Remove-Item Env:MYSQL_PASSWORD -ErrorAction SilentlyContinue
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"
```

常用参数：

```powershell
python build_repository_vectors.py `
  --model-path "C:\Users\27271\.cache\modelscope\models\thomas--text2vec-base-chinese" `
  --output-dir "C:\JAVA\search_engine" `
  --batch-size 32 `
  --max-length 512 `
  --device auto
```

处理过程：

1. 分批流式读取 `id` 和 `readme`，避免一次性加载大表。
2. 清洗 README 中的 HTML 标签和多余空白。
3. 使用模型生成 mean pooling 文档向量并归一化。
4. 使用 `numpy.lib.format.open_memmap` 预分配向量文件，避免把所有向量同时放在 Python 列表中。
5. 保存 ID、向量、FAISS 索引（可选）和元数据。

## 查询验证

```powershell
$env:PYTHONNOUSERSITE = "1"
C:\JAVA\Python314\python.exe query_repository_vectors.py --query "权限管理 系统" --top-k 5
```

输出示例：

```text
1   id=10000001   score=0.873421
2   id=10000002   score=0.812904
```

查询向量与文档向量使用同一模型、同一 mean pooling 和归一化方式。

## HTTP 检索服务

启动本地服务，默认监听 `127.0.0.1:8080`：

```powershell
cd python
$env:PYTHONNOUSERSITE = "1"
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"
C:\JAVA\Python314\python.exe serve_repository_vectors.py
```

接口：

```text
GET /health
GET /search?q=权限管理&topK=10
GET /api/search?q=权限管理&topK=10
```

示例：

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/health"
Invoke-RestMethod "http://127.0.0.1:8080/search?q=权限管理&topK=5"
```

返回结构：

```json
{
  "code": 0,
  "query": "权限管理",
  "topK": 5,
  "count": 3,
  "elapsedMs": 120.5,
  "results": [
    {
      "id": 10000030,
      "score": 0.308583,
      "fullName": "owner/repository",
      "htmlUrl": "https://gitee.com/owner/repository"
    }
  ]
}
```

说明：

- 服务启动时加载一次模型、向量和 FAISS 索引，请求复用。
- `topK` 默认 10，最大 100。
- 如果设置了 `MYSQL_PASSWORD`，服务会额外查询 `id`、`fullName`、`htmlUrl` 返回展示信息；未设置时只返回 ID 和相似度。
- 默认只监听本机；如需局域网访问，可加 `--host 0.0.0.0`。
- 8080 与 Spring Boot 默认端口相同，不能同时占用；如果后端服务已经启动，请先停止其中一个。

## 注意事项

- 模型推理默认优先使用 CUDA，没有 GPU 时使用 CPU。
- `max-length=512` 会截断超长 README，符合 BERT 模型最大位置长度。
- 向量索引是离线生成产物，不提交到代码仓库；输出目录为 `C:\JAVA\search_engine`。
- 重新执行会覆盖输出目录中的同名索引文件，建议在低峰期执行。
- 数据库密码只通过环境变量传递，不要写入脚本或提交到 Git。
