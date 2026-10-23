# -*- coding: utf-8 -*-
"""演示/路演前自检：一键确认数据库、后端、OCR、登录、数据量与导出链路。

用法：
    set DB_PASSWORD=你的密码
    .venv\\Scripts\\python tools\\preflight.py

全部通过退出码 0，任一项失败退出码 1；WARN 不影响退出码。
路径与账号可通过环境变量覆盖：BASE_URL / OCR_URL / DB_* / ADMIN_USER / ADMIN_PASSWORD
"""
import json
import os
import sys
import urllib.error
import urllib.request

import pymysql

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

BASE_URL = os.environ.get("BASE_URL", "http://127.0.0.1:8080")
OCR_URL = os.environ.get("OCR_URL", "http://127.0.0.1:8001")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "Admin@2026")

results = []


def record(name, ok, detail, warn=False):
    results.append((name, ok, detail, warn))
    tag = "WARN" if warn and not ok else ("PASS" if ok else "FAIL")
    print("[%s] %-22s %s" % (tag, name, detail))


def http_json(url, payload=None, token=None, timeout=10):
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers,
                                 method="POST" if payload is not None else "GET")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.status, json.loads(resp.read().decode("utf-8"))


def check_db():
    try:
        conn = pymysql.connect(
            host=os.environ.get("DB_HOST", "127.0.0.1"),
            port=int(os.environ.get("DB_PORT", "3306")),
            user=os.environ.get("DB_USER", "root"),
            password=os.environ.get("DB_PASSWORD", ""),
            database=os.environ.get("DB_NAME", "eye_followup_ledger"),
            charset="utf8mb4",
        )
    except Exception as e:
        record("MySQL 连接", False, str(e))
        return None
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cur:
            cur.execute("SELECT COUNT(*) c FROM patients")
            patients = cur.fetchone()["c"]
            cur.execute("SELECT COUNT(*) c FROM followup_exams")
            exams = cur.fetchone()["c"]
            cur.execute("SELECT COUNT(*) c FROM medical_images")
            images = cur.fetchone()["c"]
            cur.execute("SELECT COUNT(*) c FROM followup_plans WHERE status='OVERDUE'")
            overdue = cur.fetchone()["c"]
        record("MySQL 连接", True, "eye_followup_ledger")
        record("演示数据-患者", patients >= 10, "%d 位" % patients)
        record("演示数据-检查", exams >= 50, "%d 条" % exams)
        record("演示数据-报告图", images >= 10, "%d 张" % images, warn=True)
        record("演示数据-逾期", overdue >= 1, "%d 条" % overdue, warn=True)
        return patients
    finally:
        conn.close()


def check_http():
    try:
        status, body = http_json(BASE_URL + "/api/health")
        record("后端健康", body.get("code") == 0, "HTTP %s / %s" % (status, body.get("data", {}).get("status")))
    except Exception as e:
        record("后端健康", False, str(e))

    try:
        status, body = http_json(OCR_URL + "/api/ocr/health")
        record("OCR 服务", bool(body.get("ok")), "engine=%s" % body.get("engine"))
    except Exception as e:
        record("OCR 服务", False, str(e))

    try:
        with urllib.request.urlopen(BASE_URL + "/", timeout=10) as resp:
            html = resp.read(2000).decode("utf-8", "ignore")
        record("前端页面", '<div id="root">' in html, "SPA 静态资源已托管")
    except Exception as e:
        record("前端页面", False, str(e))


def check_api():
    token = None
    try:
        status, body = http_json(BASE_URL + "/api/auth/login",
                                 {"username": ADMIN_USER, "password": ADMIN_PASSWORD})
        token = (body.get("data") or {}).get("token")
        record("管理员登录", bool(token), "账号 %s" % ADMIN_USER)
    except Exception as e:
        record("管理员登录", False, str(e))

    if not token:
        return

    try:
        status, body = http_json(BASE_URL + "/api/dashboard/overview", token=token)
        summary = (body.get("data") or {}).get("summary") or {}
        record("看板聚合", summary.get("totalPatients", 0) > 0,
               "患者 %s / 逾期 %s" % (summary.get("totalPatients"), summary.get("overduePlans")))
    except Exception as e:
        record("看板聚合", False, str(e))

    try:
        req = urllib.request.Request(BASE_URL + "/api/export/patients",
                                     headers={"Authorization": "Bearer " + token})
        with urllib.request.urlopen(req, timeout=30) as resp:
            head = resp.read(3)
            ctype = resp.headers.get("Content-Type", "")
        record("台账导出", head == b"\xef\xbb\xbf" and "csv" in ctype, "CSV + UTF-8 BOM")
    except Exception as e:
        record("台账导出", False, str(e))

    try:
        status, body = http_json(BASE_URL + "/api/account", token=token)
        data = body.get("data") or {}
        record("个人资料接口", bool(data.get("username")), "displayName=%s" % data.get("displayName"))
    except Exception as e:
        record("个人资料接口", False, str(e))


def main():
    print("明眸随访 · 路演前自检  %s" % BASE_URL)
    print("-" * 56)
    check_db()
    check_http()
    check_api()
    print("-" * 56)
    fails = [r for r in results if not r[1] and not r[3]]
    warns = [r for r in results if not r[1] and r[3]]
    print("结果：%d 项通过，%d 项警告，%d 项失败" % (
        len([r for r in results if r[1]]), len(warns), len(fails)))
    if fails:
        print("失败项：" + "、".join(r[0] for r in fails))
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
