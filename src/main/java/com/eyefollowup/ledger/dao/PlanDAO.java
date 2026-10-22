package com.eyefollowup.ledger.dao;

import com.eyefollowup.ledger.entity.FollowupPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 随访计划数据访问。
 */
@Repository
@RequiredArgsConstructor
public class PlanDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<FollowupPlan> MAPPER = (rs, i) -> {
        FollowupPlan p = new FollowupPlan();
        p.setPlanId(rs.getInt("plan_id"));
        p.setPatientId(rs.getString("patient_id"));
        p.setStageCode(rs.getString("stage_code"));
        p.setFrequencyMonths(rs.getInt("frequency_months"));
        Date last = rs.getDate("last_exam_date");
        p.setLastExamDate(last == null ? null : last.toLocalDate());
        Date due = rs.getDate("next_due_date");
        p.setNextDueDate(due == null ? null : due.toLocalDate());
        p.setStatus(rs.getString("status"));
        p.setBasis(rs.getString("basis"));
        p.setSuggestionId((Integer) rs.getObject("suggestion_id"));
        Timestamp created = rs.getTimestamp("created_at");
        p.setCreatedAt(created == null ? null : created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        p.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        try {
            p.setPatientName(rs.getString("patient_name"));
        } catch (Exception ignored) {
            // 单表查询时无该列
        }
        try {
            p.setRiskFlag(rs.getString("risk_flag"));
        } catch (Exception ignored) {
        }
        return p;
    };

    private static final String JOIN_SELECT =
            "SELECT p.*, pa.name AS patient_name, "
          + "  (SELECT e.risk_flag FROM followup_exams e WHERE e.patient_id=p.patient_id "
          + "   ORDER BY e.exam_date DESC, e.exam_id DESC LIMIT 1) AS risk_flag "
          + "FROM followup_plans p JOIN patients pa ON pa.patient_id=p.patient_id ";

    public FollowupPlan findActive(String patientId) {
        List<FollowupPlan> list = jdbc.query(
                "SELECT * FROM followup_plans WHERE patient_id=? AND status IN ('PENDING','OVERDUE') "
                        + "ORDER BY plan_id DESC LIMIT 1", MAPPER, patientId);
        return list.isEmpty() ? null : list.get(0);
    }

    public FollowupPlan findById(int planId) {
        List<FollowupPlan> list = jdbc.query("SELECT * FROM followup_plans WHERE plan_id=?", MAPPER, planId);
        return list.isEmpty() ? null : list.get(0);
    }

    public int insert(FollowupPlan p) {
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO followup_plans (patient_id, stage_code, frequency_months, last_exam_date, "
                            + "next_due_date, status, basis, suggestion_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"plan_id"});
            ps.setString(1, p.getPatientId());
            ps.setString(2, p.getStageCode());
            ps.setInt(3, p.getFrequencyMonths() == null ? 6 : p.getFrequencyMonths());
            ps.setDate(4, p.getLastExamDate() == null ? null : Date.valueOf(p.getLastExamDate()));
            ps.setDate(5, p.getNextDueDate() == null ? null : Date.valueOf(p.getNextDueDate()));
            ps.setString(6, p.getStatus() == null ? "PENDING" : p.getStatus());
            ps.setString(7, p.getBasis());
            if (p.getSuggestionId() == null) ps.setNull(8, java.sql.Types.INTEGER); else ps.setInt(8, p.getSuggestionId());
            return ps;
        }, kh);
        Number key = kh.getKey();
        int id = key == null ? 0 : key.intValue();
        p.setPlanId(id);
        return id;
    }

    public void update(FollowupPlan p) {
        jdbc.update("UPDATE followup_plans SET stage_code=?, frequency_months=?, last_exam_date=?, next_due_date=?, "
                        + "status=?, basis=?, suggestion_id=? WHERE plan_id=?",
                p.getStageCode(), p.getFrequencyMonths(),
                p.getLastExamDate() == null ? null : Date.valueOf(p.getLastExamDate()),
                p.getNextDueDate() == null ? null : Date.valueOf(p.getNextDueDate()),
                p.getStatus(), p.getBasis(), p.getSuggestionId(), p.getPlanId());
    }

    public void updateStatus(int planId, String status) {
        jdbc.update("UPDATE followup_plans SET status=? WHERE plan_id=?", status, planId);
    }

    public int markOverdue() {
        return jdbc.update("UPDATE followup_plans SET status='OVERDUE' "
                + "WHERE status='PENDING' AND next_due_date IS NOT NULL AND next_due_date < CURDATE()");
    }

    public long countOverdue() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM followup_plans WHERE status='OVERDUE'", Long.class);
        return n == null ? 0 : n;
    }

    public List<FollowupPlan> list(String status, String keyword, int limit) {
        StringBuilder sql = new StringBuilder(JOIN_SELECT + "WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status=?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND pa.name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY FIELD(p.status,'OVERDUE','PENDING','DONE','CLOSED'), p.next_due_date ASC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public List<FollowupPlan> listOverdue(int limit) {
        return jdbc.query(JOIN_SELECT + "WHERE p.status='OVERDUE' ORDER BY p.next_due_date ASC LIMIT ?",
                MAPPER, limit);
    }

    public LocalDate latestDueDate(String patientId) {
        List<java.sql.Date> list = jdbc.query(
                "SELECT next_due_date FROM followup_plans WHERE patient_id=? AND status IN ('PENDING','OVERDUE') "
                        + "ORDER BY plan_id DESC LIMIT 1",
                (rs, i) -> rs.getDate(1), patientId);
        return list.isEmpty() || list.get(0) == null ? null : list.get(0).toLocalDate();
    }
}
