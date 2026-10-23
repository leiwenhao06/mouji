# -*- coding: utf-8 -*-
"""报告类型识别与字段提取规则。

设计（基于真实样本归纳）：
- 数据集主体为 OCULUS Pentacam「4 Maps Refractive」报告（1200x902/1200x912），
  全部关键字段集中在左侧列（x < 0.29W）：标题、患者信息、Cornea Front/Back 参数、
  角膜厚度与眼前节参数。分析时仅对左列裁剪区做 OCR，既提速又避免彩色图例数字干扰。
- classify_report(lines) 依据文本行关键词识别报告类型；
- extract_fields(report_type, items) 按「同行右侧取值 / 正下方就近取值」做空间配对，
  并对 Pentacam 的 Cornea Front / Back 两个同标签区块按纵坐标分区，输出归一化键值对。

字段覆盖（与报告实际内容逐项对齐，2026-09 复核）：
- 患者信息：姓名(Last/First Name)、性别、出生日期、病历号(ID)、检查日期、检查时间、眼别；
- Cornea Front/Back：Rf/Rs/Rm/Rmin、K1/K2/Km、Q-val、Rper、Astig、Axis、QS(OK/Warning)；
- 厚度表：Pupil Center / Pachy Apex / Thinnest Locat. 的厚度(μm) 与位置坐标(x[mm], y[mm])，
  以及 K Max. (Front) 前表面最大曲率 KMax(D) 及其位置坐标；
- 眼前节：Cornea Volume、Chamber Volume、Angle、A.C. Depth、Pupil Dia、IOP(Sum)、Lens Th；
- Corvis ST：cCBI/cTBI、DA Ratio、ARTh、SP-A1、Integrated Radius、SSI、IS-Value。
"""
import re

# ==================== 报告类型 ====================

REPORT_TYPES = [
    ("Pentacam_4Maps", "Pentacam", "Pentacam",
     ["pentacam", "4 maps", "refractive", "oculus", "belin/ambr", "k1", "k2", "km",
      "rmin", "rper", "pachy", "thinnest"]),
    ("Corvis_ST", "Corvis ST", "Corvis_TBI",
     ["corvis", "cbi", "tbi", "da ratio", "arth", "sp-a1", "integrated radius",
      "biomechanical", "ambr", "stress-strain", "ssi", "deformation"]),
    ("CASIA_OCT", "CASIA", "OCT",
     ["casia", "anterior segment", "pachymetry map", "tomey"]),
    ("Sirius_Topo", "Sirius", "Pentacam",
     ["sirius", "cso", "scheimpflug"]),
    ("Fundus", "Fundus", "Fundus",
     ["fundus", "retina", "optic disc", "macula"]),
    ("Slit_Lamp", "Slit Lamp", "Slit_Lamp",
     ["slit lamp", "slit-lamp"]),
    ("Generic_Report", "Unknown", "Other", []),
]

DEVICE_MAP = {rt: dev for rt, dev, _, _ in REPORT_TYPES}
EXAM_TYPE_MAP = {rt: et for rt, _, et, _ in REPORT_TYPES}


def classify_report(lines):
    """返回 (report_type, confidence)。"""
    text = "\n".join(lines).lower()
    best = None
    best_score = 0.0
    for rt, dev, et, kws in REPORT_TYPES:
        if not kws:
            continue
        hits = sum(1 for kw in kws if kw in text)
        if hits == 0:
            continue
        score = min(hits / max(len(kws), 1), 1.0)
        if score > best_score:
            best_score = score
            best = rt
    if best is None:
        return "Generic_Report", 0.0
    return best, best_score


# ==================== 归一化工具 ====================

def _norm_text(s):
    """全角转半角、压缩空白、去掉两侧冒号。"""
    if s is None:
        return ""
    out = []
    for ch in s:
        code = ord(ch)
        if code == 0x3000:
            out.append(" ")
        elif 0xFF01 <= code <= 0xFF5E:
            out.append(chr(code - 0xFEE0))
        else:
            out.append(ch)
    t = "".join(out).strip()
    t = re.sub(r"\s+", " ", t)
    t = re.sub(r"^[:：]+|[:：]+$", "", t)
    return t


def _num(s):
    """提取数值字符串（去单位、去千分位）。"""
    t = _norm_text(s).replace(",", "")
    m = re.search(r"[+-]?\d+(?:\.\d+)?", t)
    if not m:
        return None
    return m.group(0)


def _date(s):
    t = _norm_text(s)
    m = re.search(r"(\d{4})[年/\-.](\d{1,2})[月/\-.](\d{1,2})日?", t)
    if not m:
        return None
    try:
        return "%04d-%02d-%02d" % (int(m.group(1)), int(m.group(2)), int(m.group(3)))
    except ValueError:
        return None


def _gender(s):
    # 注意：female 包含子串 male，必须先判 Female 且 male 用 ^ 锚定，避免误判
    t = _norm_text(s).strip().lower()
    if re.search(r"女|^f$|^female", t):
        return "Female"
    if re.search(r"男|^m$|^male", t):
        return "Male"
    return None


def _age(s):
    t = _norm_text(s)
    m = re.search(r"(\d{1,3})\s*岁?", t)
    if not m:
        return None
    v = int(m.group(1))
    return str(v) if 0 <= v <= 150 else None


def _eye(s):
    t = _norm_text(s).lower()
    if re.search(r"右|od|right", t):
        return "right"
    if re.search(r"左|os|left", t):
        return "left"
    if re.search(r"双|both|ou", t):
        return "both"
    return None


def _name(s):
    t = _norm_text(s)
    m = re.search(r"[\u4e00-\u9fa5·]{2,6}", t)
    return m.group(0) if m else None


def _id_card(s):
    t = _norm_text(s)
    m = re.search(r"\d{17}[\dXx]", t)
    return m.group(0).upper() if m else None


def _patient_no(s):
    t = _norm_text(s)
    m = re.search(r"[0-9A-Za-z]{3,20}", t)
    return m.group(0) if m else None


def _time(s):
    """检查时间 HH:MM 或 HH:MM:SS。"""
    t = _norm_text(s)
    m = re.search(r"(\d{1,2}):(\d{2})(?::(\d{2}))?", t)
    if not m:
        return None
    hh, mm, ss = int(m.group(1)), int(m.group(2)), int(m.group(3) or 0)
    if not (0 <= hh <= 23 and 0 <= mm <= 59 and 0 <= ss <= 59):
        return None
    return "%02d:%02d:%02d" % (hh, mm, ss)


_TRANSFORMS = {
    "num": _num, "date": _date, "gender": _gender, "age": _age,
    "eye": _eye, "name": _name, "id_card": _id_card, "patient_no": _patient_no,
    "time": _time,
}

# 「OK / Warning」类定性字段（QS 质量规格等）：仅接受短文本，避免把相邻数值当值
_QUALITY_WORDS = ("ok", "warning", "fail", "good", "okay")


def _quality(s):
    t = _norm_text(s)
    low = t.lower()
    for w in _QUALITY_WORDS:
        if low.startswith(w) or w in low:
            return t
    return None


_TRANSFORMS["quality"] = _quality


def _clean_value(raw, transform):
    if transform == "text":
        return raw
    v = _TRANSFORMS[transform](raw)
    if v is None:
        return None
    if transform == "num":
        v = re.sub(r"[^0-9.\-]", "", v)
        if v in ("", "-", "."):
            return None
    return v


# ==================== 字段规则 ====================

# (标签正则, 归一化key, 转换器)
PATIENT_RULES = [
    (r"Last Name|Family Name", "last_name", "text"),
    (r"First Name|Given Name", "first_name", "text"),
    (r"姓名|Patient Name|^Name", "patient_name", "name"),
    (r"性别|Sex|Gender", "gender", "gender"),
    (r"年龄|^Age\s*[:：]?$|Age\s*[:：]", "age", "age"),
    (r"出生日期|Date of Birth|DOB|Birth", "date_of_birth", "date"),
    (r"身份证|ID Card|ID No", "id_card", "id_card"),
    (r"病历号|门诊号|住院号|病案号|Patient ID|MRN|Medical Record|^ID\s*[:：]?$",
     "patient_no", "patient_no"),
    (r"检查日期|Exam Date|Date of Exam", "exam_date", "date"),
    (r"^Time\b|^Exam Time\b|检查时间", "exam_time", "time"),
    (r"^Exam Info\b|检查信息", "exam_info", "text"),
    (r"诊断|Diagnosis|Impression", "diagnosis", "text"),
    # 不能用 "$" 锚定：报告同一文本行通常为「Eye: Right」，行尾还有值。
    (r"眼别|^Eye\b", "eye_side", "eye"),
]

# Pentacam「Cornea Front」区块（同标签映射为 front_*）
PENTACAM_FRONT_RULES = [
    (r"^Rf:?", "front_rf", "num"),
    (r"^Rs:?", "front_rs", "num"),
    (r"^R[i1]?m[i1]?n:?", "front_rmin", "num"),
    (r"^Rm:?", "front_rm", "num"),
    (r"^K1:?", "front_k1", "num"),
    (r"^K2:?", "front_k2", "num"),
    (r"^Km:?", "front_km", "num"),
    (r"^Q[-\s]?[Vv]al", "front_q_val", "num"),
    (r"^Rper", "front_rper", "num"),
    (r"^Astig", "front_astig", "num"),
    (r"^Axis", "front_axis", "num"),
    # QS（Quality Specification）为 OK/Warning 定性值，必须用 quality 转换器（旧版用 num 导致永远提取不到）
    (r"^QS", "front_qs", "quality"),
]

# Pentacam「Cornea Back」区块（同标签映射为 back_*）
PENTACAM_BACK_RULES = [
    (r"^Rf:?", "back_rf", "num"),
    (r"^Rs:?", "back_rs", "num"),
    (r"^R[i1]?m[i1]?n:?", "back_rmin", "num"),
    (r"^Rm:?", "back_rm", "num"),
    (r"^K1:?", "back_k1", "num"),
    (r"^K2:?", "back_k2", "num"),
    (r"^Km:?", "back_km", "num"),
    (r"^Q[-\s]?[Vv]al", "back_q_val", "num"),
    (r"^Rper", "back_rper", "num"),
    (r"^Astig", "back_astig", "num"),
    (r"^Axis", "back_axis", "num"),
    (r"^QS", "back_qs", "quality"),
]

# Pentacam 厚度/眼前节区块（无前后表面区分）
#
# 注意规则顺序与锚定（历史缺陷修复）：
# 1) 旧版首条规则为 `^Pachy:?`，会把「Pachy Apex:」整行抢占为瞳孔中心厚度，
#    导致 pachy_apex（顶点厚度）在全部 404 例中从未提取成功 → 现按实际标签精确锚定；
# 2) 报告实际标签为「K Max. (Front):」，其值是前表面最大曲率 KMax(D)（圆锥角膜诊断核心指标），
#    旧版当作厚度写入 k_max_pachy_x，语义错误 → 现独立为 front_kmax。
PENTACAM_PACHY_RULES = [
    (r"^Pupil Center", "pupil_center_pachy", "num"),
    (r"^Pachy Apex", "pachy_apex", "num"),
    (r"^Thinnest Locat", "thinnest_pachy", "num"),
    (r"^K Max", "front_kmax", "num"),
    (r"^Cornea Volume", "cornea_volume", "num"),
    (r"^Chamber Volume|AC Vol", "chamber_volume", "num"),
    (r"^Angle", "angle", "num"),
    (r"A\.? ?C\.? ?Depth|ACD", "ac_depth", "num"),
    (r"^Pupil Dia", "pupil_dia", "num"),
    # 报告底部为「Enter IOP   IOP(Sum): +3.5 mmHg」，仅取 IOP(Sum) 的数值。
    # OCR 常把字母 O 识别为数字 0（I0P），因此用 [O0] 容错。
    (r"I[O0]P\s*\(\s*Sum\s*\)|^I[O0]P\s*[:：]", "iop", "num"),
    (r"^Lens Th", "lens_th", "num"),
]

# 厚度表的 4 个「厚度 + 位置坐标(x[mm], y[mm])」条目：键 → 坐标列前缀
PACHY_COORD_PREFIX = {
    "pupil_center_pachy": "pupil_center",
    "pachy_apex": "pachy_apex",
    "thinnest_pachy": "thinnest",
    "front_kmax": "k_max",
}


# Corvis ST 生物力学
CORVIS_RULES = [
    (r"cCBI|corrected CBI", "ccbi", "num"),
    (r"cTBI|corrected TBI", "ctbi", "num"),
    (r"^CBI|Corvis Biomechanical Index", "cbi", "num"),
    (r"^TBI|Tomographic Biomechanical", "tbi", "num"),
    (r"DA Ratio|Deformation Amplitude Ratio", "da_ratio", "num"),
    (r"ARTh|Ambr", "arth", "num"),
    (r"SP[- ]?A1|Stiffness Param", "sp_a1", "num"),
    (r"Integrated Radius|Integr", "integr_radius", "num"),
    (r"SSI|Stress", "ssi", "num"),
    (r"IS[-\s]?Value|Inverse", "is_value", "num"),
]

CASIA_RULES = [
    (r"K[Ff]|Flat K|^K1", "front_k1", "num"),
    (r"K[Ss]|Steep K|^K2", "front_k2", "num"),
    (r"Avg K|^Km", "front_km", "num"),
    (r"CCT|Central", "pupil_center_pachy", "num"),
    (r"Min Thickness|Thinnest", "thinnest_pachy", "num"),
    (r"ACD|AC Depth", "ac_depth", "num"),
    (r"ACV|AC Vol", "chamber_volume", "num"),
    (r"^Angle|ACA", "angle", "num"),
    (r"Pupil", "pupil_dia", "num"),
    (r"IOP", "iop", "num"),
]


# ==================== 提取引擎 ====================

# 各字段的生理/设备合理区间（用于剔除 OCR 误读值）。
# 依据：Pentacam 与 Corvis ST 报告的量程与临床常见范围。超出区间的值不写入结果，
# 仅记入 rejected 供人工核对（如误把图例数字当成参数时 KMax 会出现 90D 以上的荒谬值）。
FIELD_RANGES = {
    "front_kmax": (30.0, 90.0),
    "front_k1": (30.0, 90.0), "front_k2": (30.0, 90.0), "front_km": (30.0, 90.0),
    "front_rf": (3.0, 12.0), "front_rs": (3.0, 12.0), "front_rm": (3.0, 12.0),
    "front_rmin": (3.0, 12.0), "front_rper": (3.0, 12.0),
    "front_astig": (0.0, 30.0), "front_axis": (0.0, 180.0), "front_q_val": (-5.0, 2.0),
    "back_k1": (-20.0, 0.0), "back_k2": (-20.0, 0.0), "back_km": (-20.0, 0.0),
    "back_rf": (3.0, 12.0), "back_rs": (3.0, 12.0), "back_rm": (3.0, 12.0),
    "back_rmin": (2.0, 12.0), "back_rper": (3.0, 12.0),
    "back_astig": (0.0, 30.0), "back_axis": (0.0, 180.0), "back_q_val": (-5.0, 2.0),
    "pupil_center_pachy": (150.0, 1200.0), "pachy_apex": (150.0, 1200.0), "thinnest_pachy": (150.0, 1200.0),
    "cornea_volume": (20.0, 150.0), "chamber_volume": (20.0, 600.0),
    "angle": (0.0, 90.0), "ac_depth": (1.0, 6.0), "pupil_dia": (0.5, 10.0),
    "iop": (0.0, 60.0), "lens_th": (0.0, 10.0),
    "ccbi": (0.0, 1.0), "ctbi": (0.0, 1.0), "cbi": (0.0, 1.0), "tbi": (0.0, 1.0),
    "arth": (0.0, 3.0), "da_ratio": (0.0, 10.0), "ssi": (0.0, 2.0),
    "is_value": (0.0, 200.0), "sp_a1": (0.0, 300.0), "integr_radius": (0.0, 30.0),
}

# 坐标列（mm）：允许负值，量程 ±8mm
COORD_RANGE = (-10.0, 10.0)


def _in_range(key, value):
    """数值是否落在合理区间内；未定义区间的字段一律通过。"""
    rng = FIELD_RANGES.get(key)
    if rng is None:
        return True
    try:
        v = float(value)
    except (TypeError, ValueError):
        return False
    return rng[0] <= v <= rng[1]


def _box_center(poly):
    if not poly or len(poly) < 4:
        return None
    xs = [p[0] for p in poly]
    ys = [p[1] for p in poly]
    return sum(xs) / 4, sum(ys) / 4


def _label_key(text, rules):
    """若文本是一个标签（可带值），返回 (key, tf, inline_value)；否则 None。"""
    t = _norm_text(text)
    if not t:
        return None
    for label_re, key, tf in rules:
        if not re.search(label_re, t, re.I):
            continue
        # 分组修正：label_re 中的 "|" 必须整体分组，否则内联值捕获只会作用于最后一个分支
        # （例："Date of Birth: 1993-11-15" 会匹配到 "Date of Birth" 但捕获不到值）。
        m = re.search(r"(?:" + label_re + r")\s*[:：]?\s*(.+)$", t, re.I)
        inline = None
        if m and m.group(1) and m.group(1).strip():
            v = _clean_value(m.group(1).strip(), tf)
            if v:
                inline = v
        return key, tf, inline
    return None


def _all_rules(report_type):
    if report_type in ("Pentacam_4Maps", "Sirius_Topo"):
        return PATIENT_RULES + PENTACAM_FRONT_RULES + PENTACAM_BACK_RULES + PENTACAM_PACHY_RULES
    return PATIENT_RULES + CORVIS_RULES + CASIA_RULES


def extract_fields(report_type, items):
    """基于「行分组 + 从左到右标签→值配对」的提取。

    items: [{text, score, poly}]（坐标为该影像分析区域的坐标）。
    规则：同一行内，标签右侧最近的可解析文本为其值；标签的值也可能出现在
    紧邻的下一行（如 Pachy 表头行与其数值行）；标签仅保留一行待配对。
    """
    fields = {}
    rejected = {}

    def set_field(key, value, item):
        if key in fields or key in rejected:
            return
        if not _in_range(key, value):
            rejected[key] = {"value": value, "raw": item["text"],
                             "reason": "数值超出合理区间 %s" % (FIELD_RANGES.get(key),),
                             "score": round(item.get("score") or 0, 4)}
            return
        fields[key] = {"value": value, "raw": item["text"],
                       "score": round(item.get("score") or 0, 4)}

    rules = _all_rules(report_type)
    is_pentacam = report_type in ("Pentacam_4Maps", "Sirius_Topo")

    # 前后表面分区：Cornea Back 标题之后的标签用 back_* 规则
    back_y = None
    for item in items:
        t = _norm_text(item["text"])
        if re.search(r"Cornea\s*Back", t, re.I):
            c = _box_center(item.get("poly"))
            if c:
                back_y = c[1]
                break
    if back_y is None and is_pentacam:
        # 兜底：第二个 "Rf:" 出现处为 Back 区
        rf_ys = []
        for item in items:
            t = _norm_text(item["text"])
            if re.match(r"^Rf\s*[:：]?", t, re.I):
                c = _box_center(item.get("poly"))
                if c:
                    rf_ys.append(c[1])
        if len(rf_ys) >= 2:
            back_y = (rf_ys[0] + rf_ys[-1]) / 2

    def section_rules(cy):
        if is_pentacam and back_y is not None and cy is not None and cy > back_y:
            return PATIENT_RULES + PENTACAM_BACK_RULES + PENTACAM_PACHY_RULES
        if is_pentacam:
            return PATIENT_RULES + PENTACAM_FRONT_RULES + PENTACAM_PACHY_RULES
        return rules

    # ---- 行分组 ----
    rows = []
    row_index_of = {}
    for idx, item in enumerate(items):
        c = _box_center(item.get("poly"))
        if c is None:
            row_index_of[idx] = -1
            continue
        cy = c[1]
        placed = False
        for r in rows:
            if abs(r["cy"] - cy) <= 12:
                r["boxes"].append(idx)
                row_index_of[idx] = rows.index(r)
                placed = True
                break
        if not placed:
            rows.append({"cy": cy, "boxes": [idx]})
            row_index_of[idx] = len(rows) - 1
    rows.sort(key=lambda r: r["cy"])
    for r in rows:
        r["boxes"].sort(key=lambda i: _box_center(items[i].get("poly"))[0])

    # ---- 顺序配对 ----
    pachy_anchor = {}  # key -> (row_idx, 值所在 x)，用于回填厚度表的位置坐标 x[mm]/y[mm]
    pending = None  # (key, tf, row_idx)

    def record(key, value, item, ri, cx):
        set_field(key, value, item)
        if key in PACHY_COORD_PREFIX and key in fields and ri is not None and cx is not None:
            pachy_anchor[key] = (ri, cx)

    for ri, row in enumerate(rows):
        for bi in row["boxes"]:
            item = items[bi]
            c = _box_center(item.get("poly"))
            rset = section_rules(c[1] if c else None)
            lab = _label_key(item["text"], rset)
            if lab is not None:
                key, tf, inline = lab
                if inline is not None:
                    record(key, inline, item, ri, c[0] if c else None)
                    pending = None
                else:
                    pending = (key, tf, ri)
                continue
            # 非标签文本：尝试作为 pending 标签的值
            if pending is not None:
                pkey, ptf, prow = pending
                # 同行取值优先；仅数值型标签允许跨到下一行（如厚度表表头与数值分行的情况），
                # 避免文本型标签（Exam Info 等）把下一行的区块标题/无关文本误当取值。
                if ri == prow or (ri == prow + 1 and ptf == "num"):
                    v = _clean_value(item["text"], ptf)
                    if v:
                        record(pkey, v, item, ri, c[0] if c else None)
                        pending = None
                        continue

    # ---- 后处理：厚度表 4 个条目的位置坐标（x[mm], y[mm]，报告中位于厚度值右侧） ----
    for key, prefix in PACHY_COORD_PREFIX.items():
        anchor = pachy_anchor.get(key)
        if not anchor or key not in fields:
            continue
        ri, value_cx = anchor
        if ri < 0 or ri >= len(rows):
            continue
        coords = []
        for bi in rows[ri]["boxes"]:
            item = items[bi]
            c = _box_center(item.get("poly"))
            if c is None or c[0] <= value_cx + 5:
                continue
            v = _num(item["text"])
            if v is None:
                continue
            try:
                if not (COORD_RANGE[0] <= float(v) <= COORD_RANGE[1]):
                    continue
            except (TypeError, ValueError):
                continue
            coords.append((v, item["text"], item.get("score")))
            if len(coords) == 2:
                break
        for idx, suffix in enumerate(("_x", "_y")):
            if idx < len(coords):
                v, raw, score = coords[idx]
                fields[prefix + suffix] = {
                    "value": v, "raw": raw, "score": round(score or 0, 4) if score else None}

    # ---- 后处理：患者姓名拼接 / 年龄推算 ----
    last = fields.get("last_name", {}).get("value")
    first = fields.get("first_name", {}).get("value")
    if (last or first) and "patient_name" not in fields:
        name = " ".join(x for x in (last, first) if x)
        fields["patient_name"] = {"value": name, "raw": name, "score": None}
    if "age" not in fields and "date_of_birth" in fields:
        try:
            from datetime import date
            dob = _date(fields["date_of_birth"]["value"])
            if dob:
                y, m, d = map(int, dob.split("-"))
                birth = date(y, m, d)
                ref = date.today()
                if "exam_date" in fields:
                    ed = _date(fields["exam_date"]["value"])
                    if ed:
                        ey, em, edd = map(int, ed.split("-"))
                        ref = date(ey, em, edd)
                age = ref.year - birth.year - ((ref.month, ref.day) < (birth.month, birth.day))
                if 0 <= age <= 150:
                    fields["age"] = {"value": str(age), "raw": "derived", "score": None}
        except Exception:
            pass
    # 被区间校验剔除的字段（不进入 fields，仅随结果返回供人工核对）
    if rejected:
        fields["_rejected"] = rejected
    return fields
