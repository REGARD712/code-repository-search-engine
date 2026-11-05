"""加载本地向量索引并执行相似度查询。"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
from pathlib import Path

import numpy as np

from embedding_utils import DEFAULT_MODEL_PATH, encode_texts, load_model

try:
    import faiss
except ImportError:
    faiss = None


LOGGER = logging.getLogger("query_repository_vectors")


def parse_args() -> argparse.Namespace:
    """解析查询参数。"""
    parser = argparse.ArgumentParser(description="查询仓库 README 向量索引")
    parser.add_argument("--query", required=True, help="查询文本")
    parser.add_argument("--top-k", type=int, default=10, help="返回条数")
    parser.add_argument(
        "--index-dir",
        default=os.getenv("VECTOR_INDEX_DIR", r"C:\JAVA\search_engine"),
        help="向量索引目录",
    )
    parser.add_argument(
        "--model-path",
        default=os.getenv("TEXT2VEC_MODEL_PATH", str(DEFAULT_MODEL_PATH)),
        help="text2vec 模型目录",
    )
    parser.add_argument("--device", default=os.getenv("EMBEDDING_DEVICE", "auto"))
    return parser.parse_args()


def load_metadata(index_dir: Path) -> dict:
    """读取索引元数据。"""
    metadata_path = index_dir / "repository_index_meta.json"
    if not metadata_path.is_file():
        raise FileNotFoundError(f"找不到索引元数据：{metadata_path}")
    return json.loads(metadata_path.read_text(encoding="utf-8"))


def main() -> int:
    """执行一次向量检索。"""
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    args = parse_args()
    if not args.query.strip():
        LOGGER.error("查询文本不能为空")
        return 2

    index_dir = Path(args.index_dir).expanduser().resolve()
    metadata = load_metadata(index_dir)

    ids_path = index_dir / metadata["files"]["ids"]
    vectors_path = index_dir / metadata["files"]["vectors"]
    ids = np.load(ids_path)
    vectors = np.load(vectors_path, mmap_mode="r")

    tokenizer, model, device, _ = load_model(args.model_path, args.device)
    query_vector = encode_texts(
        tokenizer,
        model,
        [args.query],
        device,
        max_length=int(metadata["model"]["max_length"]),
        batch_size=1,
    )[0]

    if len(ids) == 0:
        print("索引中没有文档")
        return 0

    top_k = max(1, min(args.top_k, len(ids)))
    index_type = metadata.get("index_type", "numpy_cosine")
    faiss_index_path = index_dir / metadata["files"]["faiss_index"] if metadata["files"].get("faiss_index") else None

    if index_type == "faiss_flat_ip" and faiss is not None and faiss_index_path and faiss_index_path.is_file():
        index = faiss.read_index(str(faiss_index_path))
        result_scores, positions = index.search(
            query_vector.reshape(1, -1).astype(np.float32), top_k
        )
        result_scores = result_scores[0]
        positions = positions[0]
    else:
        all_scores = np.asarray(vectors, dtype=np.float32) @ query_vector
        positions = np.argsort(-all_scores)[:top_k]
        result_scores = all_scores[positions]

    for rank, position in enumerate(positions, start=1):
        if position < 0:
            continue
        print(f"{rank}\t id={int(ids[position])}\t score={float(result_scores[rank - 1]):.6f}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
