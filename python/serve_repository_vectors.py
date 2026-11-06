"""本地文档向量检索 HTTP 服务。

启动后监听 8080 端口，根据请求中的查询词：
1. 使用 text2vec 生成查询向量；
2. 在已生成的 FAISS / numpy 向量索引中检索；
3. 返回仓库 ID、相似度和仓库基础信息。

默认读取：
- 索引目录：C:\\JAVA\\search_engine
- 模型目录：C:\\Users\\27271\\.cache\\modelscope\\models\\thomas--text2vec-base-chinese

接口：
GET /search?q=权限管理&topK=10
GET /api/search?q=权限管理&topK=10
GET /health
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import numpy as np
import pymysql

from embedding_utils import DEFAULT_MODEL_PATH, clean_text, encode_texts, load_model

try:
    import faiss
except ImportError:  # faiss 可选；没有时使用 numpy 余弦矩阵
    faiss = None


LOGGER = logging.getLogger("serve_repository_vectors")

MAX_TOP_K = 2000
DEFAULT_TOP_K = 1000


def load_metadata(index_dir: Path) -> dict:
    """读取索引元数据。"""
    path = index_dir / "repository_index_meta.json"
    if not path.is_file():
        raise FileNotFoundError(f"找不到索引元数据：{path}")
    return json.loads(path.read_text(encoding="utf-8"))


def load_repository_metadata(ids: np.ndarray) -> dict[int, dict[str, str]]:
    """从 MySQL 读取仓库展示字段，失败时降级为只返回 ID 和分数。"""
    password = os.getenv("MYSQL_PASSWORD", "")
    if not password:
        LOGGER.warning("未设置 MYSQL_PASSWORD，接口只返回 ID 和相似度，不返回仓库名称")
        return {}
    if len(ids) == 0:
        return {}

    host = os.getenv("MYSQL_HOST", "127.0.0.1")
    port = int(os.getenv("MYSQL_PORT", "3306"))
    user = os.getenv("MYSQL_USER", "root")
    database = os.getenv("MYSQL_DATABASE", "search_engine")

    connection = None
    try:
        connection = pymysql.connect(
            host=host,
            port=port,
            user=user,
            password=password,
            database=database,
            charset="utf8mb4",
            autocommit=True,
            connect_timeout=10,
            read_timeout=30,
        )
        metadata: dict[int, dict[str, str]] = {}
        id_list = [int(value) for value in ids.tolist()]
        chunk_size = 500
        with connection.cursor() as cursor:
            for start in range(0, len(id_list), chunk_size):
                chunk = id_list[start : start + chunk_size]
                placeholders = ",".join(["%s"] * len(chunk))
                sql = (
                    "SELECT `id`, `fullName`, `htmlUrl` "
                    "FROM `repository` "
                    f"WHERE `id` IN ({placeholders})"
                )
                cursor.execute(sql, chunk)
                for row in cursor.fetchall():
                    metadata[int(row[0])] = {
                        "fullName": row[1],
                        "htmlUrl": row[2],
                    }
        LOGGER.info("仓库展示信息加载完成：%s 条", len(metadata))
        return metadata
    except Exception as exception:  # 展示信息不是检索主链路，失败时降级
        LOGGER.warning("仓库展示信息加载失败，仅返回 ID 和相似度：%s", exception)
        return {}
    finally:
        if connection is not None:
            connection.close()


class VectorSearchEngine:
    """加载一次模型和索引，供 HTTP 请求复用。"""

    def __init__(self, index_dir: Path, model_path: Path, device: str):
        self.index_dir = index_dir.resolve()
        self.metadata = load_metadata(self.index_dir)
        self.ids = np.load(self.index_dir / self.metadata["files"]["ids"])
        self.vectors = np.load(
            self.index_dir / self.metadata["files"]["vectors"],
            mmap_mode="r",
        )
        self.dimension = int(self.metadata["dimension"])
        self.vector_lock = threading.Lock()

        self.faiss_index = None
        faiss_filename = self.metadata["files"].get("faiss_index")
        if (
            self.metadata.get("index_type") == "faiss_flat_ip"
            and faiss is not None
            and faiss_filename
            and (self.index_dir / faiss_filename).is_file()
        ):
            self.faiss_index = faiss.read_index(str(self.index_dir / faiss_filename))
            LOGGER.info("已加载 FAISS 索引：%s", faiss_filename)
        else:
            LOGGER.info("使用 numpy 余弦矩阵检索，文档数=%s", len(self.ids))

        self.tokenizer, self.model, self.device, self.model_dir = load_model(
            model_path,
            device,
        )
        self.repository_metadata = load_repository_metadata(self.ids)
        LOGGER.info(
            "检索服务初始化完成：文档数=%s，维度=%s，设备=%s",
            len(self.ids),
            self.dimension,
            self.device,
        )

    def search(self, query: str, top_k: int) -> list[dict]:
        """执行一次向量检索。"""
        text = clean_text(query)
        if not text or len(self.ids) == 0:
            return []

        top_k = max(1, min(top_k, len(self.ids), MAX_TOP_K))
        with self.vector_lock:
            query_vector = encode_texts(
                self.tokenizer,
                self.model,
                [text],
                self.device,
                max_length=int(self.metadata["model"].get("max_length", 512)),
                batch_size=1,
            )[0]

        if self.faiss_index is not None:
            scores, positions = self.faiss_index.search(
                query_vector.reshape(1, -1).astype(np.float32),
                top_k,
            )
            scores = scores[0]
            positions = positions[0]
        else:
            all_scores = np.asarray(self.vectors, dtype=np.float32) @ query_vector
            positions = np.argsort(-all_scores)[:top_k]
            scores = all_scores[positions]

        results = []
        for score, position in zip(scores, positions):
            if position < 0:
                continue
            repository_id = int(self.ids[position])
            repository_info = self.repository_metadata.get(repository_id, {})
            results.append(
                {
                    "id": repository_id,
                    "score": round(float(score), 6),
                    "fullName": repository_info.get("fullName"),
                    "htmlUrl": repository_info.get("htmlUrl"),
                }
            )
        return results


class VectorRequestHandler(BaseHTTPRequestHandler):
    """HTTP 请求处理器。"""

    server_version = "RepositoryVectorServer/1.0"

    def do_OPTIONS(self) -> None:
        """处理浏览器预检请求。"""
        self.send_response(HTTPStatus.NO_CONTENT)
        self._send_cors_headers()
        self.end_headers()

    def do_GET(self) -> None:
        """处理查询和健康检查。"""
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            engine: VectorSearchEngine = self.server.engine  # type: ignore[attr-defined]
            self._send_json(
                HTTPStatus.OK,
                {
                    "status": "ok",
                    "python": sys.version.split()[0],
                    "documents": len(engine.ids),
                    "dimension": engine.dimension,
                    "indexType": engine.metadata.get("index_type"),
                },
            )
            return

        if parsed.path in ("/search", "/api/search"):
            params = parse_qs(parsed.query, keep_blank_values=True)
            query = (params.get("q") or params.get("keyword") or params.get("query") or [""])[0].strip()
            if not query:
                self._send_json(
                    HTTPStatus.BAD_REQUEST,
                    {"code": 400, "message": "查询词不能为空，请使用 ?q=关键词"},
                )
                return
            if len(query) > 500:
                self._send_json(
                    HTTPStatus.BAD_REQUEST,
                    {"code": 400, "message": "查询词不能超过 500 个字符"},
                )
                return

            raw_top_k = (params.get("topK") or params.get("top_k") or [str(DEFAULT_TOP_K)])[0]
            try:
                top_k = int(raw_top_k)
            except ValueError:
                self._send_json(
                    HTTPStatus.BAD_REQUEST,
                    {"code": 400, "message": "topK 必须是整数"},
                )
                return

            started = time.perf_counter()
            engine: VectorSearchEngine = self.server.engine  # type: ignore[attr-defined]
            try:
                results = engine.search(query, top_k)
            except Exception as exception:  # 不把堆栈直接返回给客户端
                LOGGER.exception("查询失败")
                self._send_json(
                    HTTPStatus.INTERNAL_SERVER_ERROR,
                    {"code": 500, "message": f"查询失败：{exception}"},
                )
                return

            self._send_json(
                HTTPStatus.OK,
                {
                    "code": 0,
                    "query": query,
                    "topK": max(1, min(top_k, len(engine.ids), MAX_TOP_K)),
                    "count": len(results),
                    "elapsedMs": round((time.perf_counter() - started) * 1000, 2),
                    "results": results,
                },
            )
            return

        if parsed.path == "/":
            self._send_json(
                HTTPStatus.OK,
                {
                    "service": "repository-vector-search",
                    "endpoints": ["/search?q=关键词&topK=10", "/api/search?q=关键词&topK=10", "/health"],
                },
            )
            return

        self._send_json(HTTPStatus.NOT_FOUND, {"code": 404, "message": "接口不存在"})

    def _send_json(self, status: HTTPStatus, payload: dict) -> None:
        """发送 JSON 响应。"""
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self._send_cors_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_cors_headers(self) -> None:
        """允许本地前端跨域访问。"""
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def log_message(self, format_string: str, *args) -> None:
        """使用 logging 输出访问日志。"""
        LOGGER.info("%s - %s", self.address_string(), format_string % args)


def parse_args() -> argparse.Namespace:
    """解析启动参数。"""
    parser = argparse.ArgumentParser(description="本地仓库 README 向量检索 HTTP 服务")
    parser.add_argument("--host", default=os.getenv("HTTP_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("HTTP_PORT", "8080")))
    parser.add_argument(
        "--index-dir",
        default=os.getenv("VECTOR_INDEX_DIR", r"C:\JAVA\search_engine"),
    )
    parser.add_argument(
        "--model-path",
        default=os.getenv("TEXT2VEC_MODEL_PATH", str(DEFAULT_MODEL_PATH)),
    )
    parser.add_argument("--device", default=os.getenv("EMBEDDING_DEVICE", "auto"))
    return parser.parse_args()


def main() -> int:
    """启动 HTTP 服务。"""
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    args = parse_args()

    index_dir = Path(args.index_dir).expanduser().resolve()
    model_path = Path(args.model_path).expanduser()

    try:
        engine = VectorSearchEngine(index_dir, model_path, args.device)
    except Exception:
        LOGGER.exception("初始化向量检索服务失败")
        return 2

    server = ThreadingHTTPServer((args.host, args.port), VectorRequestHandler)
    server.engine = engine  # type: ignore[attr-defined]

    LOGGER.info("HTTP 服务已启动：http://%s:%s", args.host, args.port)
    LOGGER.info("查询示例：http://%s:%s/search?q=权限管理&topK=10", args.host, args.port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        LOGGER.info("收到停止信号")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
