# Code Repository Search Engine

A repository search engine built on Gitee repository READMEs. It combines:

- Jieba tokenization and an inverted index for term-frequency recall;
- `text2vec-base-chinese` and FAISS for semantic recall;
- intersection of both ID lists, returned in semantic-result order.

## Features

- Sync Gitee user repositories, falling back to organization repositories;
- Fetch repositories page by page, filter private repositories, and read READMEs;
- Store repositories in MySQL;
- Build a Jieba `SEARCH`-mode inverted index;
- Hybrid search: inverted index + AI semantic recall;
- `GET /search` endpoint returning complete repository records;
- Static HTML/CSS prototype with repository cards, pagination, and responsive layout;
- Product requirements, backend documentation, and Python vector-service documentation.

## Architecture

```text
UI / HTTP client
      │ GET /search
      ▼
Spring Boot 3.3.3 / Java 17
      │
      ├── Jieba → inverted index → IDs by term frequency
      └── HTTP → Python semantic service → IDs by similarity
                         │
                         ▼
              intersection, semantic order
                         │
                         ▼
              MySQL repository records
```

## Project Layout

```text
code-repository-search-engine/
├── backend/        Spring Boot service, inverted index, semantic client
├── python/         text2vec vector build and semantic HTTP service
├── prd/            product requirements document
├── ui/             static HTML/CSS prototype
├── README.md       Chinese README
└── README.en.md
```

## Requirements

| Component | Version |
|---|---|
| JDK | 17 |
| Maven | 3.8.1 (wrapper included) |
| MySQL | 8.x, database `search_engine` |
| Python | 3.14.0 |
| Model | local `text2vec-base-chinese` |
| Vector index | FAISS `IndexFlatIP`, numpy fallback supported |

Install Python dependencies:

```powershell
cd python
python -m pip install -r requirements.txt
python -m pip install -r requirements-optional.txt
```

## Quick Start

### 1. Create the database

Run [schema.sql](backend/src/main/resources/db/schema.sql).

Set database credentials through environment variables:

```powershell
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "your MySQL root password"
```

### 2. Build document vectors and start the semantic service

```powershell
cd python
$env:PYTHONNOUSERSITE = "1"
$env:MYSQL_PASSWORD = "your MySQL root password"

C:\JAVA\Python314\python.exe build_repository_vectors.py
C:\JAVA\Python314\python.exe serve_repository_vectors.py --host 127.0.0.1 --port 8080
```

Semantic service:

```text
GET http://127.0.0.1:8080/health
GET http://127.0.0.1:8080/search?q=large-model&topK=10
```

### 3. Build and start the Java service

```powershell
cd backend
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "your MySQL root password"

.\mvnw.cmd -DskipTests package
& "$env:JAVA_HOME\bin\java.exe" -jar target\code-repository-search-engine-0.0.1-SNAPSHOT.jar
```

The Java service listens on port `80`. Binding port 80 on Windows may require administrator privileges.

## Search API

Request:

```http
GET /search
Content-Type: application/json

{"query":"large-model"}
```

or:

```text
GET /search?query=large-model
```

Response:

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

Behavior:

- returns at most 1000 repositories;
- intersects inverted-index IDs and semantic IDs;
- returns results in semantic-result order;
- returns `[]` for an empty query or no intersection;
- falls back to inverted-index results when the Python semantic service is unavailable.

## Configuration

```yaml
server:
  port: 80

repository:
  index:
    path: ${REPOSITORY_INDEX_PATH:./data/repository-index.bin}
  semantic-search:
    base-url: ${SEMANTIC_SEARCH_BASE_URL:http://127.0.0.1:8080}
```

## UI Prototype

Open `ui/index.html` directly in a browser. See [ui/README.md](ui/README.md).

## Tests

```powershell
cd backend
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "your MySQL root password"
.\mvnw.cmd test
```

## Documentation

- [Product Requirements (Chinese)](prd/代码仓库搜索引擎-PRD-v1.0.md)
- [Backend README](backend/README.md)
- [Python semantic service](python/README.md)
- [UI prototype](ui/README.md)

## Notes

- Keep `MYSQL_PASSWORD` and `GITEE_ACCESS_TOKEN` in environment variables.
- `.idea/` is ignored to avoid committing local credentials.
- Java uses port 80; the Python semantic service uses port 8080.
- Runtime indexes and vector artifacts are not committed.

## License

[MIT](LICENSE)
