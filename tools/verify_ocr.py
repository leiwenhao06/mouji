# -*- coding: utf-8 -*-
"""OCR 导入链路校验：报告图片 → OCR 提取 → 与入库真值逐字段比对。

真值来源：种子数据生成时同一份指标既画进报告图、又写入数据库，
因此 medical_images → followup_exams → corneal_topography 即该图的期望值。
本脚本用于回归验证「导入功能真实可用」：改动了 OCR 规则或换版式后跑一遍，
字段准确率低于阈值即失败（退出码 1）。

用法：
    python tools/verify_ocr.py                 # 全量
    python tools/verify_ocr.py --limit 20      # 随机抽 20 张
    python tools/verify_ocr.py --min-accuracy 0.99
"""
import argparse
import json
import os
import random
import sys
import urllib.request

import pymysql

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

# OCR 字段 → 真值来源；tol 为允许误差（图片上的小数位决定）
# (ocr_key, table, column, tol, label)
NUMERIC_FIELDS = [
    ("front_k1", "front_k1", 0.006),
    ("front_k2", "front_k2", 0.006),
    ("front_km", "front_km", 0.006),
    ("front_kmax", "front_kmax", 0.006),
    ("front_rmin", "front_rmin", 0.006),
    ("back_k1", "back_k1", 0.006),
    ("back_k2", "back_k2", 0.006),
    ("back_km", "back_km", 0.006),
    ("back_rmin", "back_rmin", 0.006),
    ("pupil_center_pachy", "pupil_center_pachy", 0.51),
    ("pachy_apex", "pachy_apex", 0.51),
    ("thinnest_pachy", "thinnest_pachy", 0.51),
    ("cornea_volume", "cornea_volume", 0.051),
    ("ac_depth", "ac_depth", 0.006),
    ("iop", "iop", 0.051),
]

EYE_MAP = {"left": "left", "right": "right", "os": "left", "od": "right",
           "左眼": "left", "右眼": "right", "le": "left", "re": "right"}


def fetch_ground_truth(limit):
    conn = pymysql.connect(
        host=os.environ.get("DB_HOST", "127.0.0.1"),
        port=int(os.environ.get("DB_PORT", "3306")),
        user=os.environ.get("DB_USER", "root"),
        password=os.environ.get("DB_PASSWORD", ""),
        database=os.environ.get("DB_NAME", "eye_followup_ledger"),
        charset="utf8mb4",
    )
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cur:
            cur.execute(
                """
                SELECT mi.image_path, e.exam_date, e.eye_side, p.name AS patient_name,
                       t.front_k1, t.front_k2, t.front_km, t.front_kmax, t.front_rmin,
                       t.back_k1, t.back_k2, t.back_km, t.back_rmin,
                       t.pupil_center_pachy, t.pachy_apex, t.thinnest_pachy,
                       t.cornea_volume, t.ac_depth, t.iop
                FROM medical_images mi
                JOIN followup_exams e ON e.exam_id = mi.exam_id
                LEFT JOIN corneal_topography t ON t.exam_id = e.exam_id
                JOIN patients p ON p.patient_id = e.patient_id
                WHERE mi.image_path IS NOT NULL AND mi.image_path <> ''
                ORDER BY mi.image_id
                """
            )
            rows = cur.fetchall()
    finally:
        conn.close()
    if limit and limit < len(rows):
        rows = random.Random(42).sample(rows, limit)
    return rows


def call_ocr(service, paths):
    body = json.dumps({"paths": paths}).encode("utf-8")
    req = urllib.request.Request(
        service.rstrip("/") + "/api/ocr/analyze-batch",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=1800) as resp:
        return json.loads(resp.read().decode("utf-8"))


def norm_eye(v):
    if v is None:
        return ""
    s = str(v).strip().lower()
    return EYE_MAP.get(s, s)


def as_float(v):
    try:
        return float(str(v).strip().replace(",", "."))
    except Exception:
        return None


def compare_one(truth, result):
    """返回 (命中数, 字段总数, 明细列表[(字段, 期望, 实际, 是否命中)])。"""
    fields = (result or {}).get("fields") or {}
    details = []

    for ocr_key, db_col, tol in NUMERIC_FIELDS:
        expected = truth.get(db_col)
        if expected is None:
            continue
        actual = as_float((fields.get(ocr_key) or {}).get("value"))
        exp = float(expected)
        ok = actual is not None and abs(actual - exp) <= tol
        details.append((ocr_key, exp, actual, ok))

    exp_date = str(truth.get("exam_date") or "")
    act_date = str((fields.get("exam_date") or {}).get("value") or "")
    if exp_date:
        details.append(("exam_date", exp_date, act_date, act_date[:10] == exp_date[:10]))

    exp_eye = norm_eye(truth.get("eye_side"))
    act_eye = norm_eye((fields.get("eye_side") or {}).get("value"))
    if exp_eye:
        details.append(("eye_side", exp_eye, act_eye, act_eye == exp_eye))

    exp_name = (truth.get("patient_name") or "").replace(" ", "")
    act_name = str((fields.get("patient_name") or {}).get("value") or "").replace(" ", "")
    if exp_name:
        details.append(("patient_name", exp_name, act_name, act_name == exp_name))

    hits = sum(1 for d in details if d[3])
    return hits, len(details), details


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0, help="随机抽样的图片数，0=全量")
    ap.add_argument("--service", default=os.environ.get("OCR_URL", "http://127.0.0.1:8001"))
    ap.add_argument("--min-accuracy", type=float, default=0.98, help="整体字段准确率阈值")
    args = ap.parse_args()

    rows = fetch_ground_truth(args.limit)
    if not rows:
        print("没有可校验的图片记录（先运行 tools/seed_data.py 生成模拟数据）")
        return 2

    paths = [os.path.join(BASE_DIR, r["image_path"].replace("/", os.sep)) for r in rows]
    print("校验图片 %d 张，调用 OCR 服务 %s ..." % (len(paths), args.service))
    payload = call_ocr(args.service, paths)
    if not payload.get("ok"):
        print("OCR 服务调用失败:", payload.get("error"))
        return 2

    results = {r.get("image_path"): r for r in payload.get("results", [])}
    by_path = {p: r for p, r in zip(paths, payload.get("results", []))}

    total_hits = 0
    total_fields = 0
    per_field = {}
    failed_images = []

    for truth, path in zip(rows, paths):
        result = by_path.get(path) or results.get(path) or {}
        hits, count, details = compare_one(truth, result)
        total_hits += hits
        total_fields += count
        if hits < count:
            failed_images.append((os.path.basename(path), details))
        for key, exp, act, ok in details:
            stat = per_field.setdefault(key, [0, 0])
            stat[0] += 1 if ok else 0
            stat[1] += 1

    accuracy = total_hits / total_fields if total_fields else 0.0
    print("\n=== 分字段准确率 ===")
    for key in sorted(per_field):
        ok, n = per_field[key]
        print("  %-20s %3d/%-3d  %6.2f%%" % (key, ok, n, 100.0 * ok / n if n else 0))
    print("\n总计：%d/%d 字段一致，整体准确率 %.2f%%（阈值 %.0f%%）"
          % (total_hits, total_fields, accuracy * 100, args.min_accuracy * 100))

    if failed_images:
        print("\n未完全一致的图片（%d 张）：" % len(failed_images))
        for name, details in failed_images[:10]:
            bad = ["%s: 期望 %s 实际 %s" % (k, e, a) for k, e, a, ok in details if not ok]
            print("  %s -> %s" % (name, "; ".join(bad)))
        if len(failed_images) > 10:
            print("  ... 其余 %d 张省略" % (len(failed_images) - 10))

    return 0 if accuracy >= args.min_accuracy else 1


if __name__ == "__main__":
    sys.exit(main())
