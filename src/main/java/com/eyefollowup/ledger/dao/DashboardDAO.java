package com.eyefollowup.ledger.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 数据看板只读聚合查询。所有 SQL 均为静态且参数化。
 */
@Repository
@RequiredArgsConstructor
public class DashboardDAO {

    private final JdbcTemplate jdbc;

    public long totalPatients() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM patients", Long.class);
        return n == null ? 0 : n;
    }

    public long totalExams() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM followup_exams", Long.class);
        return n == null ? 0 : n;
    }

    public long examsThisMonth() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM followup_exams WHERE DATE_FORMAT(exam_date,'%Y-%m')=DATE_FORMAT(CURDATE(),'%Y-%m')",
                Long.class);
        return n == null ? 0 : n;
    }

    public long progressionPatients() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT patient_id) FROM followup_exams WHERE risk_flag IN ('PROGRESSION','SEVERE')",
                Long.class);
        return n == null ? 0 : n;
    }

    /** 每位患者最近一次检查的分期分布。 */
    public List<Map<String, Object>> stageDistribution() {
        return jdbc.queryForList(
                "SELECT stage_code AS name, COUNT(*) AS value FROM ("
              + "  SELECT patient_id, stage_code, ROW_NUMBER() OVER (PARTITION BY patient_id "
              + "         ORDER BY exam_date DESC, exam_id DESC) rn "
              + "  FROM followup_exams WHERE stage_code IS NOT NULL"
              + ") x WHERE rn=1 GROUP BY stage_code ORDER BY value DESC");
    }

    /** 每位患者最近一次检查的风险标记分布。 */
    public List<Map<String, Object>> riskDistribution() {
        return jdbc.queryForList(
                "SELECT risk_flag AS name, COUNT(*) AS value FROM ("
              + "  SELECT patient_id, risk_flag, ROW_NUMBER() OVER (PARTITION BY patient_id "
              + "         ORDER BY exam_date DESC, exam_id DESC) rn "
              + "  FROM followup_exams"
              + ") x WHERE rn=1 GROUP BY risk_flag ORDER BY value DESC");
    }

    /** 近 12 个月每月检查量。 */
    public List<Map<String, Object>> monthlyExams() {
        return jdbc.queryForList(
                "SELECT DATE_FORMAT(exam_date,'%Y-%m') AS month, COUNT(*) AS value "
              + "FROM followup_exams WHERE exam_date >= DATE_SUB(CURDATE(), INTERVAL 11 MONTH) "
              + "GROUP BY month ORDER BY month");
    }

    /** 患者年龄分布。 */
    public List<Map<String, Object>> ageDistribution() {
        // 先派生分组列再聚合，避免 only_full_group_by 对 CASE 表达式 + 别名分组报错
        return jdbc.queryForList(
                "SELECT bucket AS name, COUNT(*) AS value FROM ("
              + "  SELECT CASE "
              + "    WHEN age < 18 THEN '0-17' WHEN age < 30 THEN '18-29' WHEN age < 45 THEN '30-44' "
              + "    WHEN age < 60 THEN '45-59' ELSE '60+' END AS bucket "
              + "  FROM patients WHERE age IS NOT NULL"
              + ") x GROUP BY bucket ORDER BY bucket");
    }

    /** 进展风险患者 TOP10（按最近 KMax 降序）。 */
    public List<Map<String, Object>> progressionTop() {
        return jdbc.queryForList(
                "SELECT p.patient_id, p.name, e.exam_date, e.eye_side, e.risk_flag, "
              + "       t.front_kmax, t.thinnest_pachy FROM ("
              + "  SELECT patient_id, exam_id, exam_date, eye_side, risk_flag, ROW_NUMBER() OVER ("
              + "         PARTITION BY patient_id ORDER BY exam_date DESC, exam_id DESC) rn "
              + "  FROM followup_exams WHERE risk_flag IN ('PROGRESSION','SEVERE')"
              + ") e JOIN patients p ON p.patient_id=e.patient_id "
              + "LEFT JOIN corneal_topography t ON t.exam_id=e.exam_id "
              + "WHERE e.rn=1 ORDER BY t.front_kmax DESC LIMIT 10");
    }

    /** AI 建议采纳统计。 */
    public Map<String, Object> suggestionStats() {
        return jdbc.queryForMap(
                "SELECT COUNT(*) AS total, "
              + "       SUM(CASE WHEN adopted=1 THEN 1 ELSE 0 END) AS adopted, "
              + "       ROUND(AVG(duration_ms)) AS avg_duration_ms "
              + "FROM followup_suggestions WHERE status='SUCCESS'");
    }
}
