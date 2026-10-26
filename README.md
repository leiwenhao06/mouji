# 明眸随访 · 圆锥角膜眼健康随访台账助手

> 把每一份角膜检查报告，变成可追踪的随访台账——让该复查的患者，不被遗漏。

明眸随访是一个面向**圆锥角膜长期随访**场景的开源 Web 应用：上传检查报告图片，OCR 自动提取角膜指标并入库；规则引擎根据多次检查的变化自动判定进展、调整复查频率；逾期患者置顶提醒；可选接入大模型生成可追溯的随访管理建议。

> **演示项目声明**：仓库内所有患者数据与报告影像均由程序生成，不含任何真实患者信息。系统只做随访管理，不做诊断、不开处方、不做手术决策，输出不构成医疗建议。

---

## 目录

- [为什么需要它](#为什么需要它)
- [核心能力](#核心能力)
- [快速开始](#快速开始)
- [使用导览](#使用导览)
- [配置说明](#配置说明)
- [技术架构](#技术架构)
- [开发者指南](#开发者指南)
- [安全与隐私](#安全与隐私)
- [常见问题](#常见问题)
- [路线图](#路线图)
- [贡献](#贡献)
- [许可与免责声明](#许可与免责声明)

## 为什么需要它

圆锥角膜是以角膜扩张、局部变薄、曲率异常增高为特征的**进展性**眼病，需要长期甚至终身随访。中国圆锥角膜诊断和治疗专家共识（2023）与全球圆锥角膜共识（2015）都强调：进展判定应基于**多次随访的客观指标变化**，而不是单次测量。

基层随访的真实难点往往不在"治"，而在"跟"：

- **录入断点**：报告格式不一，KMax、最薄点等指标靠手工抄录，易错、费时；
- **判断断点**：指标的细微变化靠人脑对比，进展容易发现得太晚；
- **提醒断点**：复查时间无人盯，患者失访、逾期，错过干预窗口。

明眸随访把这三件事变成一条自动化的台账闭环。

## 核心能力

| 模块 | 说明 |
|---|---|
| 患者台账 | 建档、搜索、台账时间线；每次检查的指标、趋势图、报告图一屏可查 |
| 报告导入 | 上传报告图片，OCR 提取 20+ 项指标；人工核对后一键入库 |
| 进展检测 | 确定性规则引擎：分期阈值 + 年化 KMax 增幅 + 厚度年降幅，结果可解释、可单测 |
| 随访计划 | 按分期/进展自动调整 3/6/12 个月复查频率；逾期置顶并全局提醒 |
| AI 随访建议 | 脱敏上下文 → 本地指南检索 → 循证草稿（约 20ms）→ 可选大模型个体化 → 指南一致性守卫 |
| 数据看板 | 分期/风险分布、月度检查量、逾期名单、进展风险名单、AI 采纳统计 |
| 台账导出 | 全员汇总与单患者明细 CSV（UTF-8 BOM，Excel 直接打开） |
| 个人中心 | 昵称、头像、修改密码；头像上传有格式与魔数校验 |
| 审计追溯 | 关键操作只追加留痕，管理员可按模块/操作人查询 |
| 安全 | JWT + BCrypt、接口鉴权、登录失败限流、参数化 SQL、安全响应头 |

**产品边界**：只做随访管理，不做诊断、不开处方、不做手术决策；建议末尾固定免责声明。

## 快速开始

### 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+（推荐 20+） |
| Python | 3.10+（推荐 3.11+） |
| MySQL | 8.0 |

### 四步运行

```bat
:: 1. 初始化数据库（幂等，可重复执行）
mysql --default-character-set=utf8mb4 -u root -p < sql\init.sql

:: 2. 创建 OCR 服务的 Python 环境（只需一次）
python -m venv .venv
.venv\Scripts\pip install -r ocr_service\requirements.txt

:: 3. 构建并启动（首次运行会自动构建前端与后端）
set DB_PASSWORD=你的MySQL密码
scripts\start.bat
```

浏览器打开 <http://127.0.0.1:8080>，使用演示账号登录：

```
账号：admin
密码：Admin@2026
```

> 生产或公开演示前，请通过 `ADMIN_USER` / `ADMIN_PASSWORD` 环境变量修改初始账号，并设置 `JWT_SECRET`。

### 生成演示数据

```bat
set DB_PASSWORD=你的MySQL密码
.venv\Scripts\python tools\seed_data.py --reset
```

默认生成 30 位虚拟患者、262 次随访、60 张模拟 Pentacam 报告图，以及稳定/进展/交联术后三类病程剧本。数据重置是幂等的，演示前随时可以恢复。

### 启动前自检

```bat
set DB_PASSWORD=你的MySQL密码
.venv\Scripts\python tools\preflight.py
```

一次检查 12 项：MySQL、演示数据量、后端、OCR、前端静态页、登录、看板、CSV 导出、个人资料接口。全部通过即可开始演示。

### 手动启动（不使用脚本）

```bat
:: 后端
set DB_PASSWORD=你的MySQL密码
mvn -DskipTests package
java -jar target\eye-followup-ledger.jar

:: OCR 服务（另一个窗口）
.venv\Scripts\python ocr_service\server.py

:: 前端开发模式（可选）
cd frontend
npm install
npm run dev
```

## 使用导览

1. **看板**：先看整体运行态——在管患者、本月检查、逾期人数、进展风险人数。
2. **患者台账**：进入任一患者，查看历次检查、KMax 与最薄点趋势、最新指标环比变化。
3. **报告导入**：上传报告图片 → OCR 预览 → 核对字段 → 确认入库。入库后自动完成进展判定与随访计划调整。
4. **AI 随访建议**：选择患者生成建议，查看评估依据、复查项目、警告信号与知识引用，可一键采纳。
5. **随访计划**：逾期患者红色置顶；完成随访后标记"已随访"，计划状态随即更新。
6. **导出与审计**：患者列表可导出全员汇总 CSV，患者详情可导出个人检查明细；管理员可在审计日志中追溯关键操作。

## 配置说明

所有敏感配置都通过环境变量注入，仓库中不含任何密钥。

### 基础配置

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `DB_HOST` / `DB_PORT` | MySQL 地址 | `127.0.0.1` / `3306` |
| `DB_USER` / `DB_PASSWORD` | MySQL 账号 | `root` / 空 |
| `DB_NAME` | 数据库名 | `eye_followup_ledger` |
| `JWT_SECRET` | JWT 签名密钥（至少 32 字符） | 内置演示值，**生产必须覆盖** |
| `ADMIN_USER` / `ADMIN_PASSWORD` | 首次启动创建的管理员 | `admin` / `Admin@2026` |
| `OCR_URL` | OCR 服务地址 | `http://127.0.0.1:8001` |
| `IMAGE_DIR` / `AVATAR_DIR` | 报告图 / 头像存储目录 | `./data/images` / `./data/avatars` |

### 可选：大模型（OpenAI 兼容接口）

支持 DeepSeek、通义 DashScope 兼容模式、Moonshot、硅基流动、本地 Ollama 等。在启动窗口设置：

```bat
set LLM_ENABLED=true
set LLM_API_KEY=你的API密钥
set LLM_BASE_URL=https://api.deepseek.com/v1
set LLM_MODEL=deepseek-chat
scripts\start.bat
```

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `LLM_ENABLED` | 是否启用大模型 | `false`（纯规则引擎） |
| `LLM_API_KEY` | 供应方密钥，仅环境变量注入 | 空 |
| `LLM_BASE_URL` | OpenAI 兼容根地址 | `https://api.deepseek.com/v1` |
| `LLM_MODEL` | 模型名 | `deepseek-chat` |
| `LLM_TIMEOUT_MS` | 调用超时 | `60000` |

**密钥安全**：密钥只存在于进程环境变量；日志仅打印"已配置/未配置"；异常信息会脱敏；未配置或调用失败时自动降级为规则草稿。

本地 Ollama：`LLM_BASE_URL=http://127.0.0.1:11434/v1`、`LLM_MODEL=qwen3:4b`，无需密钥。

## 技术架构

```
┌──────────────────────────────────────────────────────────┐
│  浏览器：React 18 + TypeScript + Ant Design 5 + ECharts   │
└───────────────────────────┬──────────────────────────────┘
                            │ /api（JWT）
┌───────────────────────────▼──────────────────────────────┐
│  Spring Boot 2.7 · Java 17 · 端口 8080                    │
│  ├─ 患者 / 检查 / 随访计划                                 │
│  ├─ 进展检测引擎（确定性规则，可单测）                     │
│  ├─ AI 随访建议流水线                                      │
│  │    脱敏上下文 → 知识检索 → 循证草稿(20ms)              │
│  │    → LLM 个体化（可选）→ 指南一致性守卫 → 降级         │
│  ├─ 数据看板（TTL 缓存） / 台账导出 / 审计日志             │
│  └─ 报告导入 ──HTTP──► OCR 服务（Flask + RapidOCR, 8001） │
└───────────────────────────┬──────────────────────────────┘
                            │ JDBC（全参数化 SQL）
┌───────────────────────────▼──────────────────────────────┐
│  MySQL 8 · eye_followup_ledger · 10 张表                  │
└──────────────────────────────────────────────────────────┘
```

设计原则：

- **双引擎可降级**：规则草稿是完整可用的主路径；大模型只做个体化补充，失败不影响业务。
- **指南一致性守卫**：模型不能删减随访频率、复查项目与急症提示，避免"幻觉改医嘱"。
- **数据同源**：模拟报告图与检查数据由同一份指标生成，数据库即真值，可自动化校验。
- **参数化 SQL**：所有查询使用占位符绑定，不拼接用户输入。

## 开发者指南

### 目录结构

```
mouji/
├─ src/main/java/com/eyefollowup/ledger/
│  ├─ agent/        AI 建议流水线（上下文、检索、规则草稿、提示词、LLM 客户端）
│  ├─ controller/   REST 接口
│  ├─ service/      业务逻辑（进展检测、随访计划、导入、建议编排、导出、审计）
│  ├─ dao/          原生 JDBC 数据访问（全部参数化 SQL）
│  ├─ config/       JWT 鉴权、拦截器、线程池、SPA 托管、安全响应头
│  ├─ entity/ dto/  实体与请求/响应对象
│  └─ common/       统一响应、异常、密码策略
├─ src/test/java/   单元测试（进展规则、账号、限流、LLM 客户端）
├─ frontend/        React + TypeScript + Vite
├─ ocr_service/     Python OCR 服务（Flask + RapidOCR）
├─ tools/           模拟数据生成、OCR 校验、路演自检
├─ sql/             建库脚本
└─ scripts/         一键启动 / 停止 / 建库
```

### 构建与测试

```bat
:: 后端测试（25 个用例）
mvn test

:: 后端打包
mvn -DskipTests package

:: 前端构建
cd frontend
npm install
npm run build
```

### 验证 OCR 识别链路

```bat
:: 需先启动 OCR 服务
set DB_PASSWORD=你的MySQL密码
.venv\Scripts\python tools\verify_ocr.py
```

脚本把 `data/images` 中的报告图送入 OCR，并与数据库真值按 20 个字段逐项比对（K1/K2/Km/KMax/Rmin、前后表面、最薄点、角膜容积、前房深度、眼压、日期、眼别、姓名、性别、出生日期），输出分字段准确率；低于阈值时退出码为 1。当前基线：**60 张图 × 20 字段 = 1200/1200 一致（100%）**。

### 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，返回 JWT |
| GET | `/api/health` | 健康检查（免鉴权） |
| GET | `/api/patients` | 患者分页/搜索 |
| GET | `/api/patients/{id}/timeline` | 患者台账时间线 |
| POST | `/api/exams` | 新增检查（自动进展检测 + 计划调整） |
| POST | `/api/import/report` | 上传报告 → OCR 预览 |
| POST | `/api/import/confirm` | 确认入库 |
| GET | `/api/followup/plans` | 随访计划列表 |
| GET | `/api/followup/overdue-count` | 逾期数量 |
| POST | `/api/followup/suggestions` | 提交 AI 建议任务（异步） |
| GET | `/api/followup/suggestions/{requestId}` | 轮询任务结果 |
| GET | `/api/dashboard/overview` | 看板聚合数据 |
| GET | `/api/export/patients` | 导出全员汇总台账（CSV） |
| GET | `/api/export/patients/{id}/ledger` | 导出单患者检查明细（CSV） |
| GET/PUT | `/api/account`、`/api/account/profile` | 个人资料查询 / 昵称修改 |
| POST/GET | `/api/account/avatar` | 头像上传 / 获取 |
| PUT | `/api/account/password` | 修改密码 |
| GET | `/api/audit` | 审计日志查询（仅管理员） |

接口统一返回 `{ code, message, data }`，`code=0` 表示成功。

### 代码约定

- 后端分层：controller → service → dao；DTO 与实体分离；禁止在 controller 写业务逻辑。
- SQL 一律使用 `?` 占位符；禁止拼接用户输入。
- 所有敏感配置走环境变量；新增配置项必须提供安全的默认值或显式失败。
- 前端 API 统一走 `frontend/src/api/client.ts`，自动携带 JWT、统一错误提示。
- 提交前运行 `mvn test`、`npm run build` 与 `tools/preflight.py`。

## 安全与隐私

- **数据**：患者姓名、电话、出生日期均为程序随机生成；报告影像为程序绘制并带 `SIMULATED REPORT` 水印；`data/` 不进入版本库。
- **鉴权**：JWT（HS256）+ 拦截器保护全部 `/api/**`；用户管理与审计查询仅管理员可用；报告影像必须登录访问。
- **密码**：BCrypt 存储；创建/重置/修改统一要求 8–64 位且包含字母与数字；连续失败 5 次锁定 10 分钟。
- **上传**：头像与报告图均有大小、扩展名与魔数校验；文件名使用 UUID；影像读取限定在配置目录内。
- **日志**：审计日志只记录操作与患者 ID，不写姓名；异常信息脱敏，不泄露密钥。
- **响应头**：`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy`、`CSP frame-ancestors`。
- **密钥**：数据库密码、JWT 密钥、LLM API Key 均只通过环境变量注入；仓库中不含任何密钥。

## 常见问题

**Q：启动后页面 404 / 空白？**
A：确认 `frontend/dist/index.html` 存在；运行 `scripts\start.bat` 会自动构建，或手动 `cd frontend && npm run build`。

**Q：端口被占用？**
A：`scripts\stop.bat` 会结束 8080/8001 端口的进程；也可手动检查占用。

**Q：报告导入报"OCR 服务不可用"？**
A：确认 `.venv` 已创建并启动 `ocr_service/server.py`；未启动时可改用手动录入，不影响其他功能。

**Q：登录提示"登录失败次数过多"？**
A：同一用户名 10 分钟内失败 5 次会临时锁定；等待 10 分钟或重启后端即可清除（内存计数）。

**Q：大模型没有生效？**
A：检查 `LLM_ENABLED=true`、密钥与 `LLM_BASE_URL` 是否正确；查看 `/api/followup/agent-status` 返回的 `llmEnabled/llmHealthy`。失败会自动使用规则草稿。

**Q：如何换成真实报告？**
A：按设备版式扩展 `ocr_service/core/reports.py` 的规则表，并用 `tools/verify_ocr.py` 的"图片 → 提取 → 真值比对"方法建立回归集。当前样本为模拟版式，不代表真实设备的识别率。

## 路线图

- 接入真实 Pentacam / Corvis 报告版式与真值回归集；
- 微信小程序患者端：复查提醒、症状上报；
- 多中心随访协作与转诊；
- 随访依从性分析与干预策略；
- 与医院 HIS / 电子病历对接评估。

## 贡献

欢迎提交 Issue 与 Pull Request。建议先开 Issue 描述问题或方案；PR 请保持单一主题，附上复现步骤或验证结果，并确保：

- `mvn test` 全部通过；
- `npm run build` 成功；
- 不提交任何密钥、真实患者数据或本地环境配置（见 `.gitignore`）。

## 许可与免责声明

- 本项目采用 [MIT License](LICENSE)，可自由使用、修改与分发，请保留版权与许可声明。
- 本项目不是医疗器械，不提供诊断、治疗或处方建议；任何随访决策应由具备资质的眼科医师作出。
- 仓库中的患者、报告与指标均为程序生成的模拟数据，与任何真实个人无关。
