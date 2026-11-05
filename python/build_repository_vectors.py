"""预计算 repository 表 README 的文档向量并保存索引。

流程：
1. 连接 MySQL，只查询 repository 表的 id 和 readme；
2. 清洗 README，加载本地 text2vec-base-chinese；
3. mean pooling 生成向量并 L2 归一化；
4. 向量保存为 .npy，可选使用 faiss 建立内积索引；
5. 索引文件、ID、向量和元数据统一保存到输出目录。

默认输出目录：C:\\JAVA\\search_engine
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Sequence

import numpy as np
import pymysql
import torch
from pymysql.cursors import SSCursor
from tqdm import tqdm

from embedding_utils import (
    DEFAULT_MODEL_PATH,
    clean_text,
    encode_texts,
    load_model,
    resolve_model_dir,
)

try:
    import faiss
except ImportError:  # faiss 是可选依赖，缺少时退化为 numpy 余弦矩阵
    faiss = None


LOGGER = logging.getLogger("build_repository_vectors")


@dataclass(frozen=True)
class DatabaseConfig:
    """MySQL 连接配置。"""

    host: str
    port: int
    user: str
    password: str
    database: str


def parse_args() -> argparse.Namespace:
    """解析命令行参数，密码优先从环境变量读取。"""
    parser = argparse.ArgumentParser(description="预计算仓库 README 文档向量")
    parser.add_argument(
        "--model-path",
        default=os.getenv("TEXT2VEC_MODEL_PATH", str(DEFAULT_MODEL_PATH)),
        help="text2vec 模型根目录或 config.json 所在目录",
    )
    parser.add_argument(
        "--output-dir",
        default=os.getenv("VECTOR_INDEX_DIR", r"C:\JAVA\search_engine"),
        help="向量和索引输出目录",
    )
    parser.add_argument("--mysql-host", default=os.getenv("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--mysql-port", type=int, default=int(os.getenv("MYSQL_PORT", "3306")))
    parser.add_argument("--mysql-user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--mysql-database", default=os.getenv("MYSQL_DATABASE", "search_engine"))
    parser.add_argument(
        "--batch-size",
        type=int,
        default=int(os.getenv("EMBEDDING_BATCH_SIZE", "32")),
        help="推理批大小",
    )
    parser.add_argument(
        "--max-length",
        type=int,
        default=int(os.getenv("EMBEDDING_MAX_LENGTH", "512")),
        help="单条 README 最大 token 数",
    )
    parser.add_argument(
        "--device",
        default=os.getenv("EMBEDDING_DEVICE", "auto"),
        help="auto、cpu 或 cuda",
    )
    parser.add_argument(
        "--threads",
        type=int,
        default=int(os.getenv("EMBEDDING_THREADS", "0")),
        help="CPU 推理线程数，0 表示使用 PyTorch 默认值",
    )
    return parser.parse_args()


def connect_database(config: DatabaseConfig) -> pymysql.Connection:
    """建立 MySQL 连接。"""
    return pymysql.connect(
        host=config.host,
        port=config.port,
        user=config.user,
        password=config.password,
        database=config.database,
        charset="utf8mb4",
        cursorclass=SSCursor,
        autocommit=True,
        connect_timeout=10,
        read_timeout=300,
    )


def count_documents(connection: pymysql.Connection) -> int:
    """统计有非空 README 的仓库数量。"""
    sql = """
        SELECT COUNT(*)
        FROM repository
        WHERE readme IS NOT NULL
          AND CHAR_LENGTH(TRIM(readme)) > 0
    """
    with connection.cursor() as cursor:
        cursor.execute(sql)
        row = cursor.fetchone()
        return int(row[0]) if row else 0


def fetch_document_rows(connection: pymysql.Connection):
    """流式读取 id 和 readme，避免一次性加载全部 README。"""
    sql = """
        SELECT id, readme
        FROM repository
        WHERE readme IS NOT NULL
          AND CHAR_LENGTH(TRIM(readme)) > 0
        ORDER BY id
    """
    cursor = connection.cursor()
    cursor.execute(sql)
    try:
        while True:
            rows = cursor.fetchmany(256)
            if not rows:
                break
            for row in rows:
                yield int(row[0]), row[1]
    finally:
        cursor.close()


def trim_vectors_file(vectors_path: Path, written: int, dimension: int) -> None:
    """把预分配的向量文件裁剪为实际写入行数。"""
    if written == 0:
        np.save(vectors_path, np.empty((0, dimension), dtype=np.float32))
        return

    vectors = np.load(vectors_path, mmap_mode="r")
    if vectors.shape[0] != written:
        np.save(vectors_path, np.array(vectors[:written], dtype=np.float32))


def build_faiss_index(
    vectors_path: Path,
    faiss_index_path: Path,
    dimension: int,
    written: int,
) -> str:
    """构建并保存 faiss 内积索引；未安装 faiss 时返回 numpy 模式。"""
    if faiss is None:
        LOGGER.warning("未安装 faiss，使用归一化向量矩阵作为 numpy 余弦索引")
        return "numpy_cosine"

    index = faiss.IndexFlatIP(dimension)
    if written > 0:
        vectors = np.load(vectors_path, mmap_mode="r")
        index.add(np.asarray(vectors, dtype=np.float32))

    faiss.write_index(index, str(faiss_index_path))
    return "faiss_flat_ip"


def save_metadata(
    output_dir: Path,
    model_dir: Path,
    database_config: DatabaseConfig,
    dimension: int,
    written: int,
    max_length: int,
    index_type: str,
    started_at: datetime,
) -> None:
    """保存索引元数据，便于加载时校验模型、维度和文件。"""
    metadata = {
        "created_at": datetime.now(timezone.utc).astimezone().isoformat(),
        "build_started_at": started_at.isoformat(),
        "model": {
            "name": "text2vec-base-chinese",
            "path": str(model_dir),
            "pooling": "mean",
            "normalized": True,
            "max_length": max_length,
        },
        "database": {
            "host": database_config.host,
            "port": database_config.port,
            "database": database_config.database,
            "table": "repository",
            "columns": ["id", "readme"],
        },
        "count": written,
        "dimension": dimension,
        "metric": "cosine",
        "index_type": index_type,
        "files": {
            "ids": "repository_ids.npy",
            "vectors": "repository_vectors.npy",
            "faiss_index": "repository_faiss.index" if index_type == "faiss_flat_ip" else None,
        },
    }
    metadata_path = output_dir / "repository_index_meta.json"
    metadata_path.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def main() -> int:
    """脚本入口。"""
    args = parse_args()
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )

    model_path = Path(args.model_path).expanduser()
    output_dir = Path(args.output_dir).expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    password = os.getenv("MYSQL_PASSWORD", "")
    if not password:
        LOGGER.error("未设置 MYSQL_PASSWORD 环境变量")
        return 2

    database_config = DatabaseConfig(
        host=args.mysql_host,
        port=args.mysql_port,
        user=args.mysql_user,
        password=password,
        database=args.mysql_database,
    )

    started_at = datetime.now(timezone.utc).astimezone()
    start_time = time.perf_counter()

    model_dir = resolve_model_dir(model_path)
    LOGGER.info("模型目录：%s", model_dir)
    LOGGER.info("输出目录：%s", output_dir)

    if args.threads > 0:
        torch.set_num_threads(args.threads)

    tokenizer, model, device, model_dir = load_model(model_dir, args.device)
    dimension = int(model.config.hidden_size)
    LOGGER.info("模型加载完成，向量维度=%s，设备=%s", dimension, device)

    try:
        connection = connect_database(database_config)
    except pymysql.err.OperationalError as exception:
        error_code = exception.args[0] if exception.args else None
        if error_code == 1045:
            LOGGER.error(
                "MySQL 认证失败：请确认当前 PowerShell 窗口中的 MYSQL_PASSWORD "
                "与 MySQL root 密码完全一致（不要带多余引号或空格）"
            )
        else:
            LOGGER.error("MySQL 连接失败：%s", exception)
        return 3

    try:
        total = count_documents(connection)
        LOGGER.info("待处理 README 文档数：%s", total)

        ids_path = output_dir / "repository_ids.npy"
        vectors_path = output_dir / "repository_vectors.npy"
        faiss_index_path = output_dir / "repository_faiss.index"

        if total == 0:
            np.save(ids_path, np.empty((0,), dtype=np.int64))
            np.save(vectors_path, np.empty((0, dimension), dtype=np.float32))
            index_type = build_faiss_index(vectors_path, faiss_index_path, dimension, 0)
            save_metadata(
                output_dir, model_dir, database_config, dimension, 0,
                args.max_length, index_type, started_at,
            )
            LOGGER.warning("没有可处理的非空 README")
            return 0

        # 预分配向量文件，避免把所有向量同时保存在 Python 列表中。
        vectors = np.lib.format.open_memmap(
            vectors_path,
            mode="w+",
            dtype=np.float32,
            shape=(total, dimension),
        )
        ids = np.empty(total, dtype=np.int64)
        written = 0

        progress = tqdm(total=total, desc="编码 README", unit="doc")
        try:
            pending_ids: List[int] = []
            pending_texts: List[str] = []

            def flush_batch() -> None:
                nonlocal written
                if not pending_texts:
                    return
                embeddings = encode_texts(
                    tokenizer,
                    model,
                    pending_texts,
                    device,
                    max_length=args.max_length,
                    batch_size=len(pending_texts),
                )
                count = len(pending_texts)
                vectors[written : written + count] = embeddings
                ids[written : written + count] = np.asarray(pending_ids, dtype=np.int64)
                written += count
                pending_ids.clear()
                pending_texts.clear()

            for document_id, readme in fetch_document_rows(connection):
                text = clean_text(readme)
                if not text:
                    progress.update(1)
                    continue
                pending_ids.append(document_id)
                pending_texts.append(text)
                if len(pending_texts) >= args.batch_size:
                    flush_batch()
                progress.update(1)

            flush_batch()
        finally:
            progress.close()
            vectors.flush()
            del vectors

        # total 是原始非空 README 数；极端情况下清洗后为空，需要裁剪。
        trim_vectors_file(vectors_path, written, dimension)
        np.save(ids_path, ids[:written])

        index_type = build_faiss_index(vectors_path, faiss_index_path, dimension, written)
        save_metadata(
            output_dir, model_dir, database_config, dimension, written,
            args.max_length, index_type, started_at,
        )

        elapsed = time.perf_counter() - start_time
        LOGGER.info(
            "构建完成：文档数=%s，维度=%s，索引类型=%s，耗时=%.2fs",
            written,
            dimension,
            index_type,
            elapsed,
        )
        LOGGER.info("向量文件：%s", vectors_path)
        LOGGER.info("ID 文件：%s", ids_path)
        if index_type == "faiss_flat_ip":
            LOGGER.info("FAISS 索引：%s", faiss_index_path)
        return 0
    finally:
        connection.close()


if __name__ == "__main__":
    sys.exit(main())
