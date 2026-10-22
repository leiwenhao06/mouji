-- ============================================================
-- 明眸随访 · 圆锥角膜眼健康随访台账助手
-- MySQL 8.0 初始化脚本（幂等，可重复执行）
-- 用法：mysql --default-character-set=utf8mb4 -u root -p < sql/init.sql
-- 注意：本库仅用于演示，所有业务数据均由 tools/seed_data.py 生成模拟数据。
-- ============================================================

CREATE DATABASE IF NOT EXISTS eye_followup_ledger
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE eye_followup_ledger;

-- 1. 患者基本信息（不含身份证号、住址等敏感字段）
CREATE TABLE IF NOT EXISTS patients (
  patient_id     VARCHAR(32)  NOT NULL COMMENT '患者ID（UUID去横线）',
  name           VARCHAR(100) NOT NULL COMMENT '姓名（演示数据均为虚构）',
  gender         ENUM('Male','Female','Other') DEFAULT NULL COMMENT '性别',
  date_of_birth  DATE         DEFAULT NULL COMMENT '出生日期',
  age            INT          DEFAULT NULL COMMENT '年龄',
  contact        VARCHAR(20)  DEFAULT NULL COMMENT '联系电话（演示数据为虚拟号码）',
  medical_history TEXT        DEFAULT NULL COMMENT '既往病史',
  created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (patient_id),
  KEY idx_name (name),
  KEY idx_gender (gender)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者基本信息';

-- 2. 随访检查记录（一条记录对应一只眼一次检查）
CREATE TABLE IF NOT EXISTS followup_exams (
  exam_id     INT          NOT NULL AUTO_INCREMENT COMMENT '检查ID',
  patient_id  VARCHAR(32)  NOT NULL COMMENT '患者ID',
  exam_date   DATE         NOT NULL COMMENT '检查日期',
  exam_time   TIME         DEFAULT NULL COMMENT '检查时间',
  eye_side    ENUM('left','right','both') NOT NULL COMMENT '眼别',
  exam_type   ENUM('Pentacam','Corvis_TBI','Corvis_VSR','Other') NOT NULL DEFAULT 'Pentacam' COMMENT '检查类型',
  notes       VARCHAR(500) DEFAULT NULL COMMENT '备注',
  risk_flag   VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT '风险标记：NONE/SUSPECT/PROGRESSION/SEVERE',
  stage_code  VARCHAR(30)  DEFAULT NULL COMMENT '分期：NORMAL/SUSPECT/CLINICAL/ADVANCED/POST_CXL',
  created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (exam_id),
  KEY idx_patient_exam (patient_id, exam_date),
  KEY idx_exam_date (exam_date),
  CONSTRAINT fk_exam_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='随访检查记录';

-- 3. 角膜地形图参数（Pentacam，一次检查一条）
CREATE TABLE IF NOT EXISTS corneal_topography (
  id                    INT          NOT NULL AUTO_INCREMENT,
  exam_id               INT          NOT NULL COMMENT '检查ID',
  front_k1              DECIMAL(8,3) DEFAULT NULL COMMENT '前表面K1 (D)',
  front_k2              DECIMAL(8,3) DEFAULT NULL COMMENT '前表面K2 (D)',
  front_km              DECIMAL(8,3) DEFAULT NULL COMMENT '前表面平均K (D)',
  front_kmax            DECIMAL(8,3) DEFAULT NULL COMMENT '前表面最大曲率KMax (D)',
  front_rmin            DECIMAL(8,3) DEFAULT NULL COMMENT '前表面Rmin (mm)',
  front_q_val           DECIMAL(8,4) DEFAULT NULL COMMENT '前表面非球面系数Q',
  front_axis            DECIMAL(8,3) DEFAULT NULL COMMENT '前表面轴位 (°)',
  front_astig           DECIMAL(8,3) DEFAULT NULL COMMENT '前表面散光 (D)',
  front_qs              VARCHAR(20)  DEFAULT NULL COMMENT '前表面质量规格 QS',
  back_k1               DECIMAL(8,3) DEFAULT NULL COMMENT '后表面K1 (D)',
  back_k2               DECIMAL(8,3) DEFAULT NULL COMMENT '后表面K2 (D)',
  back_km               DECIMAL(8,3) DEFAULT NULL COMMENT '后表面平均K (D)',
  back_rmin             DECIMAL(8,3) DEFAULT NULL COMMENT '后表面Rmin (mm)',
  back_q_val            DECIMAL(8,4) DEFAULT NULL COMMENT '后表面非球面系数Q',
  back_qs               VARCHAR(20)  DEFAULT NULL COMMENT '后表面质量规格 QS',
  pupil_center_pachy    DECIMAL(8,2) DEFAULT NULL COMMENT '瞳孔中心角膜厚度 (μm)',
  pachy_apex            DECIMAL(8,2) DEFAULT NULL COMMENT '角膜顶点厚度 (μm)',
  thinnest_pachy        DECIMAL(8,2) DEFAULT NULL COMMENT '角膜最薄点厚度 (μm)',
  cornea_volume         DECIMAL(8,3) DEFAULT NULL COMMENT '角膜容积 (mm³)',
  ac_depth              DECIMAL(8,3) DEFAULT NULL COMMENT '前房深度 (mm)',
  iop                   DECIMAL(8,3) DEFAULT NULL COMMENT '眼压 (mmHg)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_topography_exam (exam_id),
  CONSTRAINT fk_topography_exam FOREIGN KEY (exam_id) REFERENCES followup_exams (exam_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角膜地形图参数';

-- 4. 角膜生物力学参数（Corvis ST，一次检查一条）
CREATE TABLE IF NOT EXISTS biomechanical_params (
  id             INT          NOT NULL AUTO_INCREMENT,
  exam_id        INT          NOT NULL COMMENT '检查ID',
  ccbi           DECIMAL(8,4) DEFAULT NULL COMMENT '角膜生物力学指数 cCBI',
  ctbi           DECIMAL(8,4) DEFAULT NULL COMMENT '角膜层析生物力学指数 cTBI',
  is_value       DECIMAL(8,4) DEFAULT NULL COMMENT '逆向凹面半径 IS-Value',
  sp_a1          DECIMAL(8,4) DEFAULT NULL COMMENT '刚度参数 SP-A1',
  integr_radius  DECIMAL(8,4) DEFAULT NULL COMMENT '综合半径 (mm)',
  arth           DECIMAL(8,4) DEFAULT NULL COMMENT 'Ambrósio相对厚度 ARTh',
  da_ratio       DECIMAL(8,4) DEFAULT NULL COMMENT '形变幅度比 DA Ratio',
  ssi            DECIMAL(8,4) DEFAULT NULL COMMENT '应力-应变指数 SSI',
  PRIMARY KEY (id),
  UNIQUE KEY uk_biomech_exam (exam_id),
  CONSTRAINT fk_biomech_exam FOREIGN KEY (exam_id) REFERENCES followup_exams (exam_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角膜生物力学参数';

-- 5. 医学影像（仅存路径，不存二进制大字段）
CREATE TABLE IF NOT EXISTS medical_images (
  image_id       INT          NOT NULL AUTO_INCREMENT,
  patient_id     VARCHAR(32)  DEFAULT NULL COMMENT '患者ID（报告预览阶段可为空）',
  exam_id        INT          DEFAULT NULL COMMENT '关联检查ID',
  image_type     ENUM('Pentacam','Corvis_TBI','Corvis_VSR','Fundus','Slit_Lamp','OCT','Other') NOT NULL DEFAULT 'Pentacam',
  eye_side       ENUM('left','right','both') DEFAULT NULL,
  image_path     VARCHAR(500) DEFAULT NULL COMMENT '相对路径（data/images/xxx.jpg）',
  file_name      VARCHAR(255) DEFAULT NULL,
  file_size      BIGINT       DEFAULT NULL,
  ocr_json       LONGTEXT     DEFAULT NULL COMMENT 'OCR结构化结果JSON',
  report_type    VARCHAR(40)  DEFAULT NULL COMMENT '报告类型',
  ocr_confidence DECIMAL(5,4) DEFAULT NULL COMMENT '识别置信度',
  upload_date    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (image_id),
  KEY idx_patient_image (patient_id, upload_date),
  KEY idx_image_exam (exam_id),
  CONSTRAINT fk_image_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id) ON DELETE SET NULL,
  CONSTRAINT fk_image_exam FOREIGN KEY (exam_id) REFERENCES followup_exams (exam_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医学影像元数据';

-- 6. 随访计划（台账核心表）
CREATE TABLE IF NOT EXISTS followup_plans (
  plan_id          INT          NOT NULL AUTO_INCREMENT,
  patient_id       VARCHAR(32)  NOT NULL,
  stage_code       VARCHAR(30)  DEFAULT NULL COMMENT '制定时的分期',
  frequency_months INT          NOT NULL DEFAULT 6 COMMENT '建议随访间隔（月）',
  last_exam_date   DATE         DEFAULT NULL COMMENT '最近一次检查日期',
  next_due_date    DATE         DEFAULT NULL COMMENT '下次应随访日期',
  status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/OVERDUE/CLOSED',
  basis            VARCHAR(500) DEFAULT NULL COMMENT '计划依据（分期/进展/术后）',
  suggestion_id    INT          DEFAULT NULL COMMENT '关联AI建议ID',
  created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (plan_id),
  KEY idx_plan_patient (patient_id, status),
  KEY idx_plan_due (next_due_date, status),
  CONSTRAINT fk_plan_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='随访计划';

-- 7. AI 随访建议
CREATE TABLE IF NOT EXISTS followup_suggestions (
  suggestion_id    INT          NOT NULL AUTO_INCREMENT,
  request_id       VARCHAR(32)  NOT NULL COMMENT '轮询ID（UUID去横线）',
  patient_id       VARCHAR(32)  DEFAULT NULL,
  input_mode       VARCHAR(20)  NOT NULL DEFAULT 'PATIENT' COMMENT 'PATIENT/REPORT',
  status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
  engine           VARCHAR(20)  DEFAULT NULL COMMENT 'llm/rules',
  summary          TEXT         DEFAULT NULL,
  result_json      MEDIUMTEXT   DEFAULT NULL COMMENT '结构化随访建议JSON',
  disclaimer       TEXT         DEFAULT NULL,
  error_message    VARCHAR(500) DEFAULT NULL,
  requested_by     VARCHAR(50)  DEFAULT NULL,
  requested_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  started_at       TIMESTAMP    NULL DEFAULT NULL,
  finished_at      TIMESTAMP    NULL DEFAULT NULL,
  wait_duration_ms BIGINT       DEFAULT NULL,
  duration_ms      BIGINT       DEFAULT NULL,
  adopted          TINYINT(1)   DEFAULT NULL COMMENT '1采纳/0不采纳/NULL未评价',
  adopted_by       VARCHAR(50)  DEFAULT NULL,
  adopted_at       TIMESTAMP    NULL DEFAULT NULL,
  PRIMARY KEY (suggestion_id),
  UNIQUE KEY uk_request (request_id),
  KEY idx_sug_patient (patient_id, requested_at),
  KEY idx_sug_status (status),
  CONSTRAINT fk_sug_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI随访建议';

-- 8. 本地医学知识库（公开指南共识摘要，供规则引擎/大模型检索）
CREATE TABLE IF NOT EXISTS local_knowledge (
  knowledge_id   INT          NOT NULL AUTO_INCREMENT,
  category       VARCHAR(30)  NOT NULL COMMENT 'guideline/drug/paper/pathway',
  title          VARCHAR(255) NOT NULL,
  content        MEDIUMTEXT   NOT NULL,
  source         VARCHAR(255) DEFAULT NULL,
  pub_year       VARCHAR(10)  DEFAULT NULL,
  evidence_level VARCHAR(30)  DEFAULT NULL,
  keywords       VARCHAR(255) DEFAULT NULL,
  enabled        TINYINT(1)   DEFAULT 1,
  created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (knowledge_id),
  UNIQUE KEY uk_kb_title (title),
  KEY idx_kb_category (category),
  KEY idx_kb_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地医学知识库';

-- 9. 操作审计日志（仅追加）
CREATE TABLE IF NOT EXISTS audit_logs (
  log_id     INT          NOT NULL AUTO_INCREMENT,
  username   VARCHAR(50)  NOT NULL,
  module     VARCHAR(30)  NOT NULL COMMENT 'PATIENT/EXAM/IMPORT/PLAN/SUGGESTION/DASHBOARD/USER',
  action     VARCHAR(50)  NOT NULL,
  detail     VARCHAR(500) DEFAULT NULL,
  patient_id VARCHAR(32)  DEFAULT NULL,
  ip         VARCHAR(64)  DEFAULT NULL,
  created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id),
  KEY idx_audit_user (username, created_at),
  KEY idx_audit_module (module, created_at),
  KEY idx_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- 10. 用户
CREATE TABLE IF NOT EXISTS users (
  id            INT          NOT NULL AUTO_INCREMENT,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name  VARCHAR(100) DEFAULT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'user' COMMENT 'admin/user',
  token         VARCHAR(64)  DEFAULT NULL,
  created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录';

-- ============================================================
-- 知识库种子数据（公开指南/共识摘要，不含具体药物剂量与疗程）
-- ============================================================
INSERT IGNORE INTO local_knowledge (category, title, content, source, pub_year, evidence_level, keywords) VALUES
('guideline', '中国圆锥角膜诊断和治疗专家共识（2023）要点',
 '圆锥角膜是一种以角膜扩张、局部变薄和曲率异常增高为特征的进展性眼病，可导致不规则散光和视力下降。诊断需结合角膜地形图/断层扫描、角膜厚度与生物力学指标综合判断。随访应关注 KMax、最薄点角膜厚度、后表面形态及屈光变化。治疗方案由眼科医师根据分期、进展速度与患者年龄个体化决定。',
 '中华医学会眼科学分会角膜病学组', '2023', '专家共识', '圆锥角膜,共识,诊断,分期,随访'),
('guideline', '全球圆锥角膜共识（Global Consensus on Keratoconus, 2015）要点',
 '全球共识提出以角膜断层扫描证据、进展证据与危险因素综合管理圆锥角膜。进展的判定应基于多次随访的客观指标变化而非单次测量；儿童、青少年及过敏体质人群进展风险更高。所有治疗决策需由眼科专科医师完成。',
 'Global Consensus on Keratoconus and Ectatic Diseases', '2015', '国际共识', 'keratoconus,progression,consensus,ectasia'),
('pathway', '圆锥角膜随访监测路径',
 '随访频率建议按风险分层：稳定期患者每 6~12 个月复查；进展期或角膜交联术后每 3~6 个月复查；儿童青少年及快速进展者适当缩短间隔。每次随访建议记录裸眼/矫正视力、验光、角膜地形图 KMax、最薄点厚度、后表面参数；有条件时联合生物力学检查。出现视力骤降、眼痛、畏光流泪等需及时就诊。',
 '院内随访管理路径（公开指南综合）', '2024', '临床路径', '随访,复查频率,KMax,角膜厚度,进展'),
('pathway', '角膜交联术后随访要点',
 '角膜交联术后需按医嘱定期复查，关注上皮愈合、角膜厚度、KMax 变化与视力恢复情况。术后早期可能出现暂时性角膜水肿或混浊，应在复查中评估。若出现持续眼痛、视力明显下降或角膜水肿加重，应及时返院。',
 '公开临床路径综合', '2024', '临床路径', '交联,CXL,术后,随访,角膜水肿'),
('guideline', '硬性透气接触镜与巩膜镜验配随访',
 '中晚期圆锥角膜患者可通过硬性透气接触镜（RGP）或巩膜镜改善视力，验配后需定期复查镜片配适、角膜健康与视力矫正效果。佩戴期间如出现眼红、眼痛、畏光或视力波动，应暂停佩戴并及时就诊。',
 '公开指南综合', '2023', '指南摘要', 'RGP,巩膜镜,接触镜,验配,随访'),
('guideline', '急性圆锥角膜水肿（急性积水）警示',
 '急性圆锥角膜水肿表现为突发视力下降、眼痛、畏光、流泪，角膜可出现局限性水肿混浊。属于需要尽快就医的情况，应避免揉眼，尽快至眼科急诊评估。缓解后仍需长期随访，因为水肿消退后角膜形态可能进一步改变。',
 '公开指南综合', '2023', '指南摘要', '急性水肿,hydrops,急症,视力下降,眼痛'),
('guideline', '儿童青少年圆锥角膜进展管理',
 '儿童与青少年圆锥角膜进展风险较高，建议缩短随访间隔并密切监测 KMax 与最薄点厚度变化。应关注揉眼、过敏性结膜炎等可干预危险因素，并进行健康教育。具体干预措施由眼科专科医师评估决定。',
 '公开指南综合', '2023', '指南摘要', '儿童,青少年,进展,揉眼,过敏'),
('drug', '圆锥角膜随访中的对症支持用药原则',
 '随访过程中如合并干眼、过敏性结膜炎等情况，可在眼科医师指导下进行对症支持处理。本系统不提供具体药物剂量、疗程或处方建议；患者不应自行长期使用眼用药物。如用药后出现不适，应及时咨询医师。',
 '公开用药安全原则', '2024', '用药安全', '干眼,过敏,对症,用药安全,遵医嘱');
