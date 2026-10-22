# -*- coding: utf-8 -*-
"""OCR 引擎封装：懒加载单例，供服务与批量分析共用。

支持两种引擎（环境变量 OCR_ENGINE 切换）：
- onnx（默认）：RapidOCR = PaddleOCR 官方 PP-OCR 模型 + onnxruntime 推理。
  同为 PaddleOCR 模型权重，CPU 上比 paddle 推理快数倍（本机实测 34s → 5.4s/张）。
- paddle：paddlepaddle 原生推理（MKLDNN 在 3.3.1 有崩溃缺陷，已关闭）。

进程级单例：配合 multiprocessing 工作池时，每个工作进程各持一份实例。
"""
import os

# paddle 路径专用：paddlepaddle 3.3.x 在部分 CPU 上 MKLDNN 推理存在 PIR 属性转换缺陷
os.environ.setdefault("FLAGS_use_mkldnn", "0")
# 跳过模型源连通性检查，加快启动
os.environ.setdefault("DISABLE_MODEL_SOURCE_CHECKS", "True")
# 限制 paddle CPU 线程数（onnx 引擎由 onnxruntime 自行管理）
os.environ.setdefault("OMP_NUM_THREADS", os.environ.get("OCR_CPU_THREADS", "4"))

ENGINE = os.environ.get("OCR_ENGINE", "onnx").strip().lower()
_OCR = None


def get_ocr():
    """返回全局 OCR 引擎实例（懒加载）。"""
    global _OCR
    if _OCR is None:
        if ENGINE == "paddle":
            from paddleocr import PaddleOCR

            kwargs = dict(
                lang="ch",
                use_doc_orientation_classify=False,
                use_doc_unwarping=False,
                use_textline_orientation=False,
                enable_mkldnn=False,
                text_detection_model_name=os.environ.get("OCR_DET_MODEL", "PP-OCRv5_mobile_det"),
                text_recognition_model_name=os.environ.get("OCR_REC_MODEL", "PP-OCRv5_mobile_rec"),
                text_recognition_batch_size=int(os.environ.get("OCR_REC_BATCH", "16")),
            )
            _OCR = PaddleOCR(**kwargs)
        else:
            from rapidocr import RapidOCR

            params = {"Global.use_cls": False}
            # 限制 onnxruntime 线程数，避免多个工作进程互相抢核
            ort_threads = os.environ.get("OCR_ORT_THREADS")
            if ort_threads:
                params["EngineConfig.onnxruntime.intra_op_num_threads"] = int(ort_threads)
            _OCR = RapidOCR(params=params)
    return _OCR


def _rapid_to_items(result):
    items = []
    if result is None:
        return items
    boxes = result.boxes if result.boxes is not None else []
    txts = result.txts if result.txts is not None else []
    scores = result.scores if result.scores is not None else []
    for i, txt in enumerate(txts):
        poly = boxes[i].tolist() if i < len(boxes) and hasattr(boxes[i], "tolist") else None
        items.append({
            "text": txt,
            "score": float(scores[i]) if i < len(scores) else None,
            "poly": poly,
        })
    return items


def ocr_image(path):
    """对单张图片执行 OCR，返回 {text, score, poly:[x1,y1,x2,y2,x3,y3,x4,y4]} 列表。"""
    import cv2
    import numpy as np

    img = cv2.imdecode(np.fromfile(str(path), dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("无法读取图片: %s" % path)
    return ocr_array(img)


def ocr_array(img):
    """对图像数组（BGR ndarray）执行 OCR。"""
    ocr = get_ocr()
    if ENGINE == "paddle":
        result = ocr.predict(img)
        items = []
        for page in result:
            rec_texts = page.get("rec_texts") or []
            rec_scores = page.get("rec_scores") or []
            polys = page.get("rec_polys") or []
            for i, txt in enumerate(rec_texts):
                poly = None
                if i < len(polys):
                    p = polys[i]
                    if hasattr(p, "tolist"):
                        p = p.tolist()
                    poly = p
                items.append({
                    "text": txt,
                    "score": float(rec_scores[i]) if i < len(rec_scores) else None,
                    "poly": poly,
                })
        return items
    return _rapid_to_items(ocr(img))
