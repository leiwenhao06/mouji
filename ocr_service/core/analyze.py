# -*- coding: utf-8 -*-
"""影像分析核心：读取影像 → 关键区域裁剪 → OCR → 报告类型识别 → 字段提取。

Pentacam「4 Maps Refractive」等报告的全部关键字段集中在左侧列
（x < 0.28W）：标题、患者信息、Cornea Front/Back 参数、厚度与眼前节参数。
仅对左列裁剪区做 OCR，大幅减少彩色图例数字等噪声，同时提升速度。
"""
import cv2
import numpy as np

from . import reports
from .ocr_engine import ocr_array

# 左列裁剪比例（关键字段所在区域；含标题与患者信息头）
CROP_LEFT_RATIO = float(__import__("os").environ.get("OCR_CROP_LEFT_RATIO", "0.28"))
CROP_BOTTOM_RATIO = float(__import__("os").environ.get("OCR_CROP_BOTTOM_RATIO", "0.94"))


def analyze_image(image_path):
    img = cv2.imdecode(np.fromfile(str(image_path), dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("无法读取图片: %s" % image_path)
    h, w = img.shape[:2]

    # 左列裁剪（关键字段区）；宽度过小的图片（如眼底照）不做裁剪
    if w >= 700:
        crop = img[0:int(h * CROP_BOTTOM_RATIO), 0:max(int(w * CROP_LEFT_RATIO), 240)]
    else:
        crop = img

    items = ocr_array(crop)
    lines = [it["text"] for it in items]
    report_type, confidence = reports.classify_report(lines)
    fields = reports.extract_fields(report_type, items)
    # 被区间校验剔除的可疑读数（OCR 误读），单独返回供人工核对，不参与业务映射
    rejected = fields.pop("_rejected", {}) if isinstance(fields, dict) else {}
    device = reports.DEVICE_MAP.get(report_type, "Unknown")
    exam_type = reports.EXAM_TYPE_MAP.get(report_type, "Other")
    return {
        "image_type": report_type,
        "device": device,
        "exam_type": exam_type,
        "confidence": round(confidence, 4),
        "fields": fields,
        "rejected": rejected,
        "lines": lines,
    }
