#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""明眸随访 · 模拟数据生成器。

生成内容：
  - 虚拟患者（姓名/出生日期/联系方式均为虚构）
  - 每位患者 2~6 次随访检查（双眼），按「稳定 / 进展 / 交联术后」三种病程剧本演化
  - 可被 OCR 规则识别的模拟 Pentacam 报告图（版式与 ocr_service/core/reports.py 对齐）
  - 自动分期/进展标记与随访计划（含部分逾期样本）

用法：
  set DB_PASSWORD=你的密码
  python tools/seed_data.py --patients 30 --reset
"""

import argparse
import datetime as dt
import math
import os
import random
import sys
import uuid

import pymysql
from PIL import Image, ImageDraw, ImageFont

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMAGE_DIR = os.path.join(BASE_DIR, "data", "images")

SURNAMES = list("林陈王张李赵黄周吴徐孙马朱胡郭何高罗郑梁谢宋唐许韩冯邓曹彭曾萧田董潘袁蔡蒋余于杜叶程魏苏吕丁任沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤")
GIVEN = list("嘉禾子安思远晨曦雨桐一鸣若溪书瑶明轩知远清和景行云舒星野安然沐阳可欣亦辰彦霖沐辰思齐若谷静姝予安致远念初南乔映雪")

HISTORIES = ["近视", "干眼", "过敏性结膜炎", "既往配戴硬性接触镜", "无特殊"]


def load_font(size):
    candidates = [
        r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\simhei.ttf",
        r"C:\Windows\Fonts\simsun.ttc",
        r"C:\Windows\Fonts\arial.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def detect(stage_scenario, history, current):
    """与 Java ProgressionDetector 相同的规则口径。history 为该眼历史（不含本次）。"""
    kmax = current["kmax"]
    thin = current["thin"]
    back_rmin = current["back_rmin"]
    ctbi = current["ctbi"]

    if stage_scenario == "post_cxl":
        stage = "POST_CXL"
    elif thin < 400 or kmax >= 52.0:
        stage = "ADVANCED"
    elif kmax >= 47.2:
        stage = "CLINICAL"
    elif kmax >= 46.0 or back_rmin < 5.0 or ctbi >= 0.8:
        stage = "SUSPECT"
    else:
        stage = "NORMAL"

    progression = False
    baseline = None
    for h in history:
        if (current["date"] - h["date"]).days >= 60:
            if baseline is None or h["date"] > baseline["date"]:
                baseline = h
    if baseline is not None:
        days = (current["date"] - baseline["date"]).days
        if days >= 90:
            months = days / 30.4375
            annual_k = (kmax - baseline["kmax"]) * 12.0 / months
            annual_t = (baseline["thin"] - thin) * 12.0 / months
            if annual_k >= 1.0 or annual_t >= 10.0:
                progression = True

    if thin < 400:
        risk = "SEVERE"
    elif progression:
        risk = "PROGRESSION"
    elif stage != "NORMAL":
        risk = "SUSPECT"
    else:
        risk = "NONE"
    return stage, risk


def plan_frequency(stage, risk):
    if risk in ("PROGRESSION", "SEVERE") or stage == "POST_CXL":
        return 3
    if stage in ("CLINICAL", "ADVANCED", "SUSPECT"):
        return 6
    return 12


STAGE_LABEL = {
    "NORMAL": "稳定/正常", "SUSPECT": "可疑", "CLINICAL": "临床期",
    "ADVANCED": "重度", "POST_CXL": "交联术后", "UNKNOWN": "未分期",
}
RISK_LABEL = {
    "NONE": "无", "SUSPECT": "需关注", "PROGRESSION": "进展", "SEVERE": "重度风险",
}


def add_months(d, months):
    y = d.year + (d.month - 1 + months) // 12
    m = (d.month - 1 + months) % 12 + 1
    day = min(d.day, [31, 29 if y % 4 == 0 and (y % 100 != 0 or y % 400 == 0) else 28,
                      31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m - 1])
    return dt.date(y, m, day)


def draw_map(draw, x, y, w, h, seed, title, font):
    """绘制一块仿地形图热力图（仅视觉效果，不参与 OCR）。"""
    rnd = random.Random(seed)
    colors = [(20, 60, 160), (30, 130, 200), (40, 190, 180), (90, 200, 90),
              (230, 210, 70), (240, 150, 50), (220, 70, 50)]
    draw.rectangle([x, y, x + w, y + h], fill=(248, 250, 252), outline=(210, 218, 228))
    draw.text((x + 8, y - 20 if y > 24 else y + 4), title, font=font, fill=(30, 55, 85))
    cx = x + w / 2 + rnd.uniform(-w * 0.08, w * 0.08)
    cy = y + h / 2 + rnd.uniform(-h * 0.06, h * 0.06)
    max_r = min(w, h) * 0.46
    for i in range(30, 0, -1):
        t = i / 30.0
        color = colors[min(len(colors) - 1, int((1 - t) * len(colors)))]
        rx = max_r * t * rnd.uniform(0.92, 1.08)
        ry = rx * rnd.uniform(0.82, 1.02)
        draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=color)
    draw.line([x + w / 2, y, x + w / 2, y + h], fill=(255, 255, 255), width=1)
    draw.line([x, y + h / 2, x + w, y + h / 2], fill=(255, 255, 255), width=1)


def generate_report_image(patient, exam_date, exam_time, eye, m, path):
    """生成版式与 ocr_service/core/reports.py 对齐的模拟 Pentacam 报告图。"""
    W, H = 1200, 900
    img = Image.new("RGB", (W, H), "white")
    draw = ImageDraw.Draw(img)
    font = load_font(19)
    title_font = load_font(21)
    small = load_font(14)

    draw.text((18, 14), "OCULUS - Pentacam 4 Maps Refractive", font=title_font, fill=(18, 40, 72))

    eye_text = "Right" if eye == "right" else "Left"
    lines = [
        f"Name: {patient['name']}",
        f"Gender: {'Male' if patient['gender'] == 'Male' else 'Female'}",
        f"Date of Birth: {patient['dob']}",
        f"Exam Date: {exam_date}",
        f"Time: {exam_time}",
        f"Eye: {eye_text}",
        "",
        "Cornea Front.",
        f"K1: {m['k1']:.2f}",
        f"K2: {m['k2']:.2f}",
        f"Km: {m['km']:.2f}",
        f"K Max. (Front): {m['kmax']:.2f}",
        f"Rmin: {m['front_rmin']:.2f}",
        f"Astig: {m['astig']:.2f}",
        f"Axis: {m['axis']:.1f}",
        "QS: OK",
        "",
        "Cornea Back.",
        f"K1: {m['back_k1']:.2f}",
        f"K2: {m['back_k2']:.2f}",
        f"Km: {m['back_km']:.2f}",
        f"Rmin: {m['back_rmin']:.2f}",
        "QS: OK",
        "",
        "Pachymetry:",
        f"Pupil Center: {m['pupil']:.0f}",
        f"Pachy Apex: {m['apex']:.0f}",
        f"Thinnest Locat: {m['thin']:.0f}",
        f"Cornea Volume: {m['volume']:.1f}",
        f"A.C. Depth: {m['acd']:.2f}",
        f"IOP (Sum): {m['iop']:.1f}",
    ]
    y = 44
    for line in lines:
        draw.text((18, y), line, font=font, fill=(25, 35, 48))
        y += 24

    # 右侧 2x2 仿地形图
    left = 360
    top = 70
    panel_w = 400
    panel_h = 340
    gap = 22
    draw_map(draw, left, top, panel_w, panel_h, patient["pid"] + "f", "Sagittal Curvature (Front)", small)
    draw_map(draw, left + panel_w + gap, top, panel_w, panel_h, patient["pid"] + "bf", "Elevation (Front)", small)
    draw_map(draw, left, top + panel_h + 46, panel_w, panel_h, patient["pid"] + "bb", "Elevation (Back)", small)
    draw_map(draw, left + panel_w + gap, top + panel_h + 46, panel_w, panel_h, patient["pid"] + "p", "Pachymetry", small)

    draw.text((18, 862), "SIMULATED REPORT - GENERATED FOR DEMO ONLY", font=small, fill=(150, 160, 175))
    img.save(path, "JPEG", quality=86)


def main():
    parser = argparse.ArgumentParser(description="生成明眸随访演示数据")
    parser.add_argument("--patients", type=int, default=30)
    parser.add_argument("--reset", action="store_true", help="清空业务数据后重新生成")
    parser.add_argument("--images", choices=["latest", "none"], default="latest")
    parser.add_argument("--seed", type=int, default=2026)
    args = parser.parse_args()

    random.seed(args.seed)
    host = os.environ.get("DB_HOST", "127.0.0.1")
    port = int(os.environ.get("DB_PORT", "3306"))
    user = os.environ.get("DB_USER", "root")
    password = os.environ.get("DB_PASSWORD", "")
    dbname = os.environ.get("DB_NAME", "eye_followup_ledger")

    conn = pymysql.connect(host=host, port=port, user=user, password=password,
                           database=dbname, charset="utf8mb4", autocommit=False)
    cur = conn.cursor()

    if args.reset:
        for table in ["followup_suggestions", "medical_images", "followup_plans",
                      "corneal_topography", "biomechanical_params", "followup_exams",
                      "audit_logs", "patients"]:
            cur.execute("DELETE FROM %s" % table)
        if os.path.isdir(IMAGE_DIR):
            for name in os.listdir(IMAGE_DIR):
                if len(name) == 36 and name.endswith(".jpg"):
                    try:
                        os.remove(os.path.join(IMAGE_DIR, name))
                    except OSError:
                        pass
        conn.commit()
        print("已清空业务数据（保留 users / local_knowledge）")

    os.makedirs(IMAGE_DIR, exist_ok=True)
    today = dt.date.today()
    used_names = set()
    total_exams = 0
    total_images = 0
    overdue_targets = []

    for idx in range(args.patients):
        while True:
            name = random.choice(SURNAMES) + "".join(random.choice(GIVEN) for _ in range(random.choice([1, 2])))
            if name not in used_names:
                used_names.add(name)
                break
        pid = uuid.uuid4().hex
        dob = dt.date(1955, 1, 1) + dt.timedelta(days=random.randint(0, 365 * 57))
        age = today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))
        gender = random.choice(["Male", "Female"])
        contact = "130" + "".join(str(random.randint(0, 9)) for _ in range(8))
        history = random.choice(HISTORIES)

        cur.execute(
            "INSERT INTO patients (patient_id, name, gender, date_of_birth, age, contact, medical_history) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s)",
            (pid, name, gender, dob, age, contact, history))

        scenario = random.choices(["stable", "progressive", "post_cxl"], weights=[60, 30, 10])[0]
        visits = random.randint(2, 6)
        interval = random.randint(4, 8)
        # 最后一次检查距今天 0~10 个月（约 30% 落在本月，保证看板有当月数据）
        if random.random() < 0.3:
            last_offset_days = random.randint(0, 25)
        else:
            last_offset_days = random.randint(0, 300 if scenario == "stable" else 110)
        last_date = today - dt.timedelta(days=last_offset_days)
        dates = [add_months(last_date, -interval * i) for i in range(visits)][::-1]

        base_kmax = {"stable": random.uniform(42.5, 45.8),
                     "progressive": random.uniform(44.0, 47.0),
                     "post_cxl": random.uniform(50.0, 56.0)}[scenario]
        base_thin = {"stable": random.uniform(470, 540),
                     "progressive": random.uniform(430, 500),
                     "post_cxl": random.uniform(380, 450)}[scenario]
        dk = random.uniform(0.35, 0.75)
        dt_thin = random.uniform(4.0, 8.0)

        eye_metrics = {"right": [], "left": []}
        latest_exam_id = {}
        for eye_index, eye in enumerate(["right", "left"]):
            eye_kmax_offset = random.uniform(-0.4, 0.4)
            eye_thin_offset = random.uniform(-4, 10)
            hist = []
            for vi, d in enumerate(dates):
                if scenario == "stable":
                    kmax = base_kmax + random.gauss(0, 0.12)
                    thin = base_thin + random.gauss(0, 2.0)
                    ctbi = clamp(0.30 + (kmax - 42) * 0.05 + random.gauss(0, 0.03), 0.05, 0.75)
                elif scenario == "progressive":
                    kmax = base_kmax + vi * dk + random.gauss(0, 0.10)
                    thin = base_thin - vi * dt_thin + random.gauss(0, 1.5)
                    ctbi = clamp(0.35 + vi * 0.07 + (kmax - 44) * 0.05 + random.gauss(0, 0.02), 0.1, 0.98)
                else:
                    kmax = base_kmax - vi * 0.12 + random.gauss(0, 0.10)
                    thin = base_thin + vi * 1.5 + random.gauss(0, 1.5)
                    ctbi = clamp(0.55 + random.gauss(0, 0.04), 0.2, 0.95)
                kmax += eye_kmax_offset
                thin += eye_thin_offset
                kmax = clamp(kmax, 40.0, 62.0)
                thin = clamp(thin, 320.0, 620.0)
                astig = clamp((kmax - 42) * 0.35 + random.uniform(0.5, 1.8), 0.5, 8.0)
                k1 = kmax - astig * 0.85
                k2 = kmax - random.uniform(0.0, 0.25)
                km = (k1 + k2) / 2
                front_rmin = clamp(7.9 - (kmax - 42) * 0.16 + random.gauss(0, 0.05), 3.6, 8.6)
                back_rmin = clamp(5.7 - (kmax - 42) * 0.10 + random.gauss(0, 0.03), 2.9, 6.6)
                back_km = -(5.9 + (kmax - 42) * 0.10)
                back_k1 = back_km - random.uniform(0.1, 0.5)
                back_k2 = back_km + random.uniform(0.1, 0.5)
                m = {
                    "date": d, "kmax": kmax, "thin": thin, "ctbi": ctbi,
                    "k1": k1, "k2": k2, "km": km, "astig": astig,
                    "axis": random.uniform(0, 180), "front_rmin": front_rmin,
                    "back_k1": back_k1, "back_k2": back_k2, "back_km": back_km,
                    "back_rmin": back_rmin, "pupil": thin + random.uniform(10, 18),
                    "apex": thin + random.uniform(5, 10), "volume": random.uniform(56, 62),
                    "acd": random.uniform(2.9, 3.6), "iop": random.uniform(11.5, 18.0),
                    "ccbi": clamp(ctbi - random.uniform(0.02, 0.08), 0.05, 1.0),
                    "is_value": random.uniform(0.8, 1.6),
                    "sp_a1": random.uniform(80, 110),
                    "integr_radius": random.uniform(5.5, 7.0),
                    "arth": random.uniform(380, 460),
                    "da_ratio": random.uniform(1.2, 1.6),
                    "ssi": random.uniform(0.65, 0.95),
                }
                stage, risk = detect(scenario, hist, m)
                notes = "角膜交联术后随访" if scenario == "post_cxl" else ""
                cur.execute(
                    "INSERT INTO followup_exams (patient_id, exam_date, exam_time, eye_side, exam_type, notes, "
                    "risk_flag, stage_code) VALUES (%s,%s,%s,%s,'Pentacam',%s,%s,%s)",
                    (pid, d, "09:%02d:00" % random.randint(0, 59), eye, notes, risk, stage))
                exam_id = cur.lastrowid
                latest_exam_id[eye] = exam_id
                cur.execute(
                    "INSERT INTO corneal_topography (exam_id, front_k1, front_k2, front_km, front_kmax, front_rmin, "
                    "front_q_val, front_axis, front_astig, front_qs, back_k1, back_k2, back_km, back_rmin, back_q_val, "
                    "back_qs, pupil_center_pachy, pachy_apex, thinnest_pachy, cornea_volume, ac_depth, iop) "
                    "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,'OK',%s,%s,%s,%s,%s,'OK',%s,%s,%s,%s,%s,%s)",
                    (exam_id, k1, k2, km, kmax, front_rmin, -0.25, m["axis"], astig,
                     back_k1, back_k2, back_km, back_rmin, 0.15,
                     round(m["pupil"]), round(m["apex"]), round(thin),
                     m["volume"], m["acd"], m["iop"]))
                cur.execute(
                    "INSERT INTO biomechanical_params (exam_id, ccbi, ctbi, is_value, sp_a1, integr_radius, arth, "
                    "da_ratio, ssi) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                    (exam_id, m["ccbi"], ctbi, m["is_value"], m["sp_a1"], m["integr_radius"],
                     m["arth"], m["da_ratio"], m["ssi"]))
                hist.append(m)
                eye_metrics[eye].append(m)
                total_exams += 1

        # 随访计划：按双眼较重的一侧制定
        last = max(eye_metrics["right"][-1], eye_metrics["left"][-1], key=lambda x: x["date"])
        worst_stage, worst_risk = "NORMAL", "NONE"
        order = {"NONE": 0, "SUSPECT": 1, "PROGRESSION": 2, "SEVERE": 3}
        for eye in ["right", "left"]:
            m = eye_metrics[eye][-1]
            stage, risk = detect(scenario, eye_metrics[eye][:-1], m)
            if order[risk] > order[worst_risk]:
                worst_risk = risk
            if stage != "NORMAL":
                worst_stage = stage
        freq = plan_frequency(worst_stage, worst_risk)
        next_due = add_months(last["date"], freq)
        basis = "分期：%s；风险：%s；建议每 %d 个月复查。依据：最近一次检查 KMax %.1fD、最薄点厚度 %.0fμm。" % (
            STAGE_LABEL.get(worst_stage, worst_stage), RISK_LABEL.get(worst_risk, worst_risk),
            freq, last["kmax"], last["thin"])
        cur.execute(
            "INSERT INTO followup_plans (patient_id, stage_code, frequency_months, last_exam_date, next_due_date, "
            "status, basis) VALUES (%s,%s,%s,%s,%s,'PENDING',%s)",
            (pid, worst_stage, freq, last["date"], next_due, basis))
        if random.random() < 0.18:
            overdue_targets.append(cur.lastrowid)

        # 报告图：每位患者每只眼最近一次检查各一张
        if args.images == "latest":
            for eye in ["right", "left"]:
                m = eye_metrics[eye][-1]
                fname = uuid.uuid4().hex + ".jpg"
                path = os.path.join(IMAGE_DIR, fname)
                generate_report_image(
                    {"pid": pid, "name": name, "gender": gender, "dob": dob.isoformat()},
                    m["date"].isoformat(), "09:%02d:00" % random.randint(0, 59), eye, m, path)
                rel = "data/images/" + fname
                cur.execute(
                    "INSERT INTO medical_images (patient_id, exam_id, image_type, eye_side, image_path, file_name, "
                    "file_size, report_type, ocr_confidence) VALUES (%s,%s,'Pentacam',%s,%s,%s,%s,'Pentacam_4Maps',0.9500)",
                    (pid, latest_exam_id[eye], eye, rel, fname, os.path.getsize(path)))
                total_images += 1

    # 制造部分逾期样本（医生长期未调整计划的场景）
    for plan_id in overdue_targets:
        days = random.randint(10, 120)
        cur.execute(
            "UPDATE followup_plans SET next_due_date = DATE_SUB(CURDATE(), INTERVAL %s DAY), status='OVERDUE' "
            "WHERE plan_id=%s", (days, plan_id))

    conn.commit()
    cur.execute("SELECT COUNT(*) FROM patients")
    p_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM followup_plans WHERE status='OVERDUE'")
    o_count = cur.fetchone()[0]
    print("生成完成：患者 %d 位，检查 %d 条，报告图 %d 张，逾期计划 %d 条" %
          (p_count, total_exams, total_images, o_count))
    conn.close()


if __name__ == "__main__":
    main()
