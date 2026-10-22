# -*- coding: utf-8 -*-
"""PaddleOCR 信息提取服务（本地 HTTP 服务）。

对外接口：
- GET  /api/ocr/health        健康检查
- POST /api/ocr/analyze       分析一张影像 {"image_path": "绝对路径"} -> 结构化结果
- POST /api/ocr/analyze-batch 批量分析 {"paths": [...]} -> {"results": [...]}

提速设计（不改硬件）：
- 多进程工作池：每个工作进程持有独立的 PaddleOCR 实例，并发处理多张影像；
- 工作进程数 OCR_WORKERS（默认 2）、每进程线程数 OCR_CPU_THREADS（默认 3）可调。
"""
import os
import sys
import time
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from flask import Flask, jsonify, request

app = Flask(__name__)

_POOL = None


def _init_worker():
    """工作进程初始化：在导入引擎前设置环境变量。"""
    os.environ.setdefault("FLAGS_use_mkldnn", "0")
    os.environ.setdefault("DISABLE_MODEL_SOURCE_CHECKS", "True")
    os.environ.setdefault("OMP_NUM_THREADS", os.environ.get("OCR_CPU_THREADS", "3"))
    # onnxruntime 每工作进程线程数（多进程并行时避免互相抢核，默认 3）
    os.environ.setdefault("OCR_ORT_THREADS", os.environ.get("OCR_ORT_THREADS", "3"))


def _analyze_worker(image_path):
    """工作进程内的分析函数（延迟导入引擎，进程级单例）。"""
    from core.analyze import analyze_image

    try:
        result = analyze_image(image_path)
        result["ok"] = True
        return result
    except Exception as e:
        import traceback

        traceback.print_exc()
        return {"ok": False, "error": "OCR 分析异常: %s" % e}


def get_pool():
    global _POOL
    if _POOL is None:
        n = int(os.environ.get("OCR_WORKERS", "3"))
        _POOL = ProcessPoolExecutor(max_workers=max(1, n), initializer=_init_worker)
    return _POOL


def _validate(path):
    p = Path(path)
    if not p.is_file():
        raise ValueError("文件不存在: %s" % path)
    return str(p)


@app.route("/api/ocr/health", methods=["GET"])
def health():
    return jsonify({"ok": True, "service": "paddle-ocr",
                    "engine": os.environ.get("OCR_ENGINE", "onnx"),
                    "workers": int(os.environ.get("OCR_WORKERS", "3")),
                    "version": "1.2"})


@app.route("/api/ocr/analyze", methods=["POST"])
def analyze():
    data = request.get_json(force=True, silent=True) or {}
    image_path = (data.get("image_path") or "").strip()
    if not image_path:
        return jsonify({"ok": False, "error": "缺少 image_path 参数"})
    try:
        _validate(image_path)
    except ValueError as e:
        return jsonify({"ok": False, "error": str(e)})
    t0 = time.time()
    try:
        result = get_pool().submit(_analyze_worker, image_path).result(timeout=900)
        result["elapsed"] = round(time.time() - t0, 2)
        return jsonify(result)
    except Exception as e:
        import traceback

        traceback.print_exc()
        return jsonify({"ok": False, "error": "OCR 服务异常: %s" % e})


@app.route("/api/ocr/analyze-batch", methods=["POST"])
def analyze_batch():
    data = request.get_json(force=True, silent=True) or {}
    paths = data.get("paths") or []
    if not isinstance(paths, list) or not paths:
        return jsonify({"ok": False, "error": "缺少 paths 参数"})
    valid = []
    for p in paths:
        try:
            valid.append(_validate(str(p)))
        except ValueError:
            continue
    if not valid:
        return jsonify({"ok": False, "error": "paths 中无有效文件"})
    t0 = time.time()
    try:
        futures = [get_pool().submit(_analyze_worker, p) for p in valid]
        results = []
        for f, p in zip(futures, valid):
            try:
                r = f.result(timeout=900)
            except Exception as e:
                r = {"ok": False, "error": "OCR 服务异常: %s" % e, "image_path": p}
            results.append(r)
        return jsonify({"ok": True, "elapsed": round(time.time() - t0, 2), "results": results})
    except Exception as e:
        import traceback

        traceback.print_exc()
        return jsonify({"ok": False, "error": "OCR 服务异常: %s" % e})


if __name__ == "__main__":
    port = int(os.environ.get("OCR_PORT", "8001"))
    host = os.environ.get("OCR_HOST", "127.0.0.1")
    app.run(host=host, port=port, threaded=True)
