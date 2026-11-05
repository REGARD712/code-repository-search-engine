"""text2vec 文档向量相关工具。

本模块负责：
1. 定位本地 ModelScope 模型快照目录；
2. 加载 text2vec-base-chinese 的 BERT 模型和 tokenizer；
3. 清洗 README 文本；
4. 使用 mean pooling 生成句向量并做 L2 归一化；
5. 批量编码文本。

说明：本地模型目录只有 BERT 权重，没有 sentence-transformers 的
modules.json，因此采用 BERT 常用的 mean pooling；归一化后使用内积
等价于余弦相似度。
"""

from __future__ import annotations

import html
import re
from pathlib import Path
from typing import List, Sequence

import numpy as np
import torch
from transformers import AutoModel, AutoTokenizer

DEFAULT_MODEL_PATH = Path(
    r"C:\Users\27271\.cache\modelscope\models\thomas--text2vec-base-chinese"
)

# HTML 标签清理，避免 README 中的 img/href 标签文本污染语义。
_HTML_TAG_PATTERN = re.compile(r"<[^>]+>", flags=re.IGNORECASE | re.DOTALL)
_SCRIPT_STYLE_PATTERN = re.compile(
    r"<(script|style)\b[^>]*>.*?</\1>",
    flags=re.IGNORECASE | re.DOTALL,
)
_WHITESPACE_PATTERN = re.compile(r"\s+")


def resolve_model_dir(model_path: str | Path) -> Path:
    """定位实际包含 config.json 的模型目录。

    ModelScope 下载缓存通常为：
    <model_root>/snapshots/master
    也允许直接传入 snapshots/master 或包含 config.json 的目录。
    """
    root = Path(model_path).expanduser().resolve()
    if (root / "config.json").is_file():
        return root

    snapshots = root / "snapshots"
    if snapshots.is_dir():
        candidates = sorted(
            (path for path in snapshots.iterdir() if (path / "config.json").is_file()),
            key=lambda path: path.name,
        )
        if candidates:
            # master 优先；没有 master 时使用字典序最大的快照。
            master = snapshots / "master"
            if (master / "config.json").is_file():
                return master
            return candidates[-1]

    raise FileNotFoundError(f"未在模型目录中找到 config.json：{root}")


def clean_text(text: str | None) -> str:
    """清洗 README 文本。

    只做通用文本清洗，不做中文分词；text2vec 会自行处理中文语义。
    """
    if not text:
        return ""

    value = html.unescape(str(text))
    value = _SCRIPT_STYLE_PATTERN.sub(" ", value)
    value = _HTML_TAG_PATTERN.sub(" ", value)
    value = value.replace("\x00", " ")
    value = _WHITESPACE_PATTERN.sub(" ", value)
    return value.strip()


def resolve_device(device: str) -> torch.device:
    """解析运行设备。"""
    if device == "auto":
        return torch.device("cuda" if torch.cuda.is_available() else "cpu")
    resolved = torch.device(device)
    if resolved.type == "cuda" and not torch.cuda.is_available():
        raise RuntimeError("当前环境没有可用的 CUDA 设备")
    return resolved


def load_model(
    model_path: str | Path = DEFAULT_MODEL_PATH,
    device: str = "auto",
) -> tuple[AutoTokenizer, AutoModel, torch.device, Path]:
    """加载本地 text2vec 模型。

    Returns:
        tokenizer、model、device、实际模型目录。
    """
    model_dir = resolve_model_dir(model_path)
    tokenizer = AutoTokenizer.from_pretrained(str(model_dir), use_fast=True)
    model = AutoModel.from_pretrained(str(model_dir))
    resolved_device = resolve_device(device)
    model.to(resolved_device)
    model.eval()
    return tokenizer, model, resolved_device, model_dir


def encode_texts(
    tokenizer: AutoTokenizer,
    model: AutoModel,
    texts: Sequence[str],
    device: torch.device,
    max_length: int = 512,
    batch_size: int = 32,
) -> np.ndarray:
    """将文本编码为归一化的文档向量。

    mean pooling 只统计 attention_mask 为 1 的 token，随后做 L2 归一化。
    """
    if not texts:
        hidden_size = int(getattr(model.config, "hidden_size", 0))
        return np.empty((0, hidden_size), dtype=np.float32)

    all_embeddings: List[np.ndarray] = []
    for start in range(0, len(texts), batch_size):
        batch = list(texts[start : start + batch_size])
        encoded = tokenizer(
            batch,
            padding=True,
            truncation=True,
            max_length=max_length,
            return_tensors="pt",
        )
        encoded = {key: value.to(device) for key, value in encoded.items()}

        with torch.inference_mode():
            output = model(**encoded)
            last_hidden_state = output.last_hidden_state
            attention_mask = encoded["attention_mask"].unsqueeze(-1).to(last_hidden_state.dtype)
            summed = (last_hidden_state * attention_mask).sum(dim=1)
            token_counts = attention_mask.sum(dim=1).clamp(min=1e-6)
            embeddings = summed / token_counts
            embeddings = torch.nn.functional.normalize(embeddings, p=2, dim=1)

        all_embeddings.append(embeddings.detach().cpu().numpy().astype(np.float32))

    return np.vstack(all_embeddings)
