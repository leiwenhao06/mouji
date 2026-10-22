package com.eyefollowup.ledger.dao;

import com.eyefollowup.ledger.entity.FollowupSuggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * AI 随访建议数据访问。
 */
@Repository
@RequiredArgsConstructor
public class SuggestionDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<FollowupSuggestion> MAPPER = (rs, i) -> {
        FollowupSuggestion s = new FollowupSuggestion();
        s.setSuggestionId(rs.getInt("suggestion_id"));
        s.setRequestId(rs.getString("request_id"));
        s.setPatientId(rs.getString("patient_id"));
        s.setInputMode(rs.getString("input_mode"));
        s.setStatus(rs.getString("status"));
        s.setEngine(rs.getString("engine"));
        s.setSummary(rs.getString("summary"));
        s.setResultJson(rs.getString("result_json"));
        s.setDisclaimer(rs.getString("disclaimer"));
        s.setErrorMessage(rs.getString("error_message"));
        s.setRequestedBy(rs.getString("requested_by"));
        s.setRequestedAt(toLdt(rs.getTimestamp("requested_at")));
        s.setStartedAt(toLdt(rs.getTimestamp("started_at")));
        s.setFinishedAt(toLdt(rs.getTimestamp("finished_at")));
        s.setWaitDurationMs((Long) rs.getObject("wait_duration_ms"));
        s.setDurationMs((Long) rs.getObject("duration_ms"));
        Object adopted = rs.getObject("adopted");
        s.setAdopted(adopted == null ? null : rs.getBoolean("adopted"));
        s.setAdoptedBy(rs.getString("adopted_by"));
        s.setAdoptedAt(toLdt(rs.getTimestamp("adopted_at")));
        try {
            s.setPatientName(rs.getString("patient_name"));
        } catch (Exception ignored) {
        }
        return s;
    };

    private static java.time.LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    public void insertPending(FollowupSuggestion s) {
        jdbc.update("INSERT INTO followup_suggestions (request_id, patient_id, input_mode, status, requested_by) "
                        + "VALUES (?, ?, ?, 'PENDING', ?)",
                s.getRequestId(), s.getPatientId(), s.getInputMode(), s.getRequestedBy());
    }

    public void markRunning(String requestId) {
        jdbc.update("UPDATE followup_suggestions SET status='RUNNING', started_at=NOW() WHERE request_id=?",
                requestId);
    }

    public void markSuccess(String requestId, String engine, String summary, String resultJson,
                            String disclaimer, long waitMs, long durationMs) {
        jdbc.update("UPDATE followup_suggestions SET status='SUCCESS', engine=?, summary=?, result_json=?, "
                        + "disclaimer=?, wait_duration_ms=?, duration_ms=?, finished_at=NOW() WHERE request_id=?",
                engine, summary, resultJson, disclaimer, waitMs, durationMs, requestId);
    }

    public void markFailed(String requestId, String message) {
        jdbc.update("UPDATE followup_suggestions SET status='FAILED', error_message=?, finished_at=NOW() "
                + "WHERE request_id=?", message, requestId);
    }

    public FollowupSuggestion findByRequestId(String requestId) {
        List<FollowupSuggestion> list = jdbc.query("SELECT * FROM followup_suggestions WHERE request_id=?",
                MAPPER, requestId);
        return list.isEmpty() ? null : list.get(0);
    }

    public FollowupSuggestion findById(int id) {
        List<FollowupSuggestion> list = jdbc.query("SELECT * FROM followup_suggestions WHERE suggestion_id=?",
                MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    private static final String JOIN_SELECT =
            "SELECT s.*, p.name AS patient_name FROM followup_suggestions s "
          + "LEFT JOIN patients p ON p.patient_id = s.patient_id ";

    public List<FollowupSuggestion> listByPatient(String patientId, int limit) {
        return jdbc.query(JOIN_SELECT + "WHERE s.patient_id=? ORDER BY s.requested_at DESC LIMIT ?",
                MAPPER, patientId, limit);
    }

    public List<FollowupSuggestion> listRecent(int limit) {
        return jdbc.query(JOIN_SELECT + "ORDER BY s.requested_at DESC LIMIT ?", MAPPER, limit);
    }

    public void updateAdoption(int id, boolean adopted, String username) {
        jdbc.update("UPDATE followup_suggestions SET adopted=?, adopted_by=?, adopted_at=NOW() WHERE suggestion_id=?",
                adopted, username, id);
    }
}
