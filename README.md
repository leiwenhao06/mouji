# 明眸随访 · 圆锥角膜眼健康随访台账助手

> 2026 华为开发者大赛 · 福建赛区参赛作品
> 方向：AI + 行业解决方案（健康台账辅助）

圆锥角膜是一种需要长期甚至终身随访的进展性眼病。基层眼健康管理中最常见的痛点不是"不会治"，而是**随访失访、指标变化靠人脑记、复查时间没人盯**。明眸随访把每一次角膜检查报告变成结构化台账，自动检测进展、自动生成随访计划、到期提醒，并给出**随访管理建议**（不涉及诊断与处方）。

> ⚠️ 本项目为比赛演示作品：**全部患者数据与报告影像均由程序生成，不含任何真实患者信息**。系统输出仅用于随访管理参考，不构成诊断或治疗意见。

---

## 1. 功能一览

| 模块 | 能力 |
|---|---|
| 患者台账 | 患者建档、搜索、台账时间线 |
| 检查台账 | KMax / 最薄点厚度 / 后表面参数 / cTBI 等指标录入与趋势图 |
| 报告导入 | 上传报告图片 → OCR 自动提取 → 人工核对 → 自动入台账 |
| 进展检测 | 规则引擎自动计算分期与年化进展（KMax 年升幅、厚度年降幅） |
| 随访计划 | 按分期/进展自动生成复查频率，逾期患者置顶提醒 |
| AI 随访建议 | 脱敏上下文 → 本地知识检索 → 循证草稿 → 大模型个体化（可选）→ 指南一致性守卫 |
| 数据看板 | 分期分布、风险分布、月度检查量、逾期名单、进展风险名单 |
| 安全与审计 | JWT 登录、接口鉴权、操作审计、报告图片需登录访问 |

## 2. 技术架构

```
浏览器（React 18 + TypeScript + Ant Design 5 + ECharts）
        │  /api
Spring Boot 2.7 (Java 17) ── 8080
   ├─ 患者/检查/随访计划 CRUD
   ├─ 报告导入 ──HTTP──► OCR 服务 (Python Flask + RapidOCR) 8001
   ├─ 进展检测引擎（确定性规则，阈值固化并可单测）
   ├─ AI 随访建议 Agent
   │     ├─ 循证规则草稿（毫秒级、可溯源）
   │     ├─ 本地知识库检索（MySQL local_knowledge）
   │     └─ Ollama 本地大模型（可选，不可用自动降级）
   ├─ 数据看板（只读聚合 + TTL 缓存）
   └─ 审计日志
MySQL 8 ── eye_followup_ledger（10 张表）
```

设计要点：
- **双引擎可降级**：大模型不可用/超时/输出不合法时，直接使用同一输出契约的循证规则草稿，功能不中断。
- **指南一致性守卫**：大模型只能补充个体化内容，不能删减规则草稿中的随访频率、复查项目与急症提示。
- **隐私红线**：Agent 上下文只含性别/年龄/眼别/检查指标；报告图片接口必须登录；无任何硬编码密码。

## 3. 快速开始

### 3.1 环境要求

- JDK 17、Maven 3.8+
- Node.js 18+（推荐 20+）
- Python 3.10+（推荐 3.11/3.13）
- MySQL 8.0

### 3.2 初始化数据库

```bat
mysql --default-character-set=utf8mb4 -u root -p < sql\init.sql
```
> 数据库名 `eye_followup_ledger`；脚本幂等，可重复执行。

### 3.3 启动后端

```bat
set DB_PASSWORD=你的MySQL密码
mvn -DskipTests package
java -jar target\eye-followup-ledger.jar
```
后端默认端口 8080，首次启动自动创建管理员 `admin / Admin@2026`（可通过环境变量 `ADMIN_USER`/`ADMIN_PASSWORD` 覆盖）。

### 3.4 启动 OCR 服务（报告导入需要）

```bat
python -m venv .venv
.venv\Scripts\pip install -r ocr_service\requirements.txt
.venv\Scripts\python ocr_service\server.py
```
默认监听 `127.0.0.1:8001`。OCR 不可用时，系统会提示并可转手动录入，不影响其他功能。

### 3.5 构建并启动前端

```bat
cd frontend
npm install
npm run build
```
构建产物在 `frontend/dist`，由后端直接托管。访问 <http://127.0.0.1:8080>。

开发模式：`npm run dev`（5173 端口，`/api` 代理到 8080）。

### 3.6 一键启动（Windows）

```bat
scripts\start.bat    :: 启动 OCR + 后端并打开浏览器
scripts\stop.bat     :: 停止服务
```

## 4. 生成演示数据

```bat
set DB_PASSWORD=你的MySQL密码
.venv\Scripts\python tools\seed_data.py --patients 30 --reset
```
生成内容：30 位虚拟患者 × 2~6 次随访（稳定/进展/交联术后三种病程剧本）、对应模拟 Pentacam 报告图、自动分期与随访计划（含逾期样本）。

> 报告图为程序绘制，版式与 OCR 规则库对齐，用于打通"生成 → 识别 → 入库"闭环；后续接入真实设备报告时可按实际版式扩展规则。

## 5. 可选：接入本地大模型

安装 [Ollama](https://ollama.com/) 并拉取模型后启动：

```bat
ollama pull qwen3:4b
set LLM_ENABLED=true
java -jar target\eye-followup-ledger.jar
```
- 默认 `LLM_ENABLED=false`，即纯规则引擎（约 2~3 秒出结果）。
- 接入后建议配 8GB 显存；CPU 推理会明显变慢。
- 关键配置：`think=false`（推理模型关闭思维链，否则可能返回空内容）、`num_ctx=8192`。

## 6. 目录结构

```
mouji/
├─ src/main/java/com/eyefollowup/ledger/   Spring Boot 后端
│  ├─ agent/       AI 随访建议流水线（上下文/检索/规则草稿/提示词/LLM）
│  ├─ controller/  REST 接口
│  ├─ service/     业务逻辑（进展检测、随访计划、导入、建议编排）
│  ├─ dao/         原生 JDBC 数据访问（全部参数化 SQL）
│  └─ config/      JWT 鉴权、线程池、SPA 托管
├─ frontend/       React + TypeScript 前端
├─ ocr_service/    Python OCR 服务（Flask + RapidOCR）
├─ tools/          模拟数据生成器
├─ sql/            建库脚本
├─ scripts/        一键启动/停止
└─ docs/           方案与迭代记录
```

## 7. 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录 |
| GET | `/api/patients` | 患者分页/搜索 |
| GET | `/api/patients/{id}/timeline` | 患者台账时间线 |
| POST | `/api/exams` | 新增检查（自动进展检测 + 计划调整） |
| POST | `/api/import/report` | 上传报告 → OCR 预览 |
| POST | `/api/import/confirm` | 确认入库 |
| GET | `/api/followup/plans` | 随访计划列表 |
| GET | `/api/followup/overdue-count` | 逾期数量 |
| POST | `/api/followup/suggestions` | 提交 AI 建议任务 |
| GET | `/api/followup/suggestions/{requestId}` | 轮询任务结果 |
| GET | `/api/dashboard/overview` | 看板聚合数据 |

## 8. 数据与合规声明

- 患者姓名、出生日期、联系方式均为程序随机生成，报告影像为程序绘制，**不含真实患者数据**。
- 系统不做诊断结论、不推荐药物剂量/疗程、不做手术决策；输出末尾固定免责声明。
- 报告图片接口需登录访问；数据库密码、JWT 密钥均通过环境变量注入，仓库中不含密钥。
- 日志中不记录患者姓名等直接标识。

## 9. 后续迭代

- 接入真实 Pentacam/Corvis 报告版式与真实影像分类模型（可按样本微调）
- 微信小程序端随访提醒与患者上报
- 多中心随访协作与转诊
- 随访依从性分析与干预策略
