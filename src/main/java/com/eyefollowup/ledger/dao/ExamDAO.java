package com.eyefollowup.ledger.dao;

import com.eyefollowup.ledger.entity.BiomechanicalParams;
import com.eyefollowup.ledger.entity.CornealTopography;
import com.eyefollowup.ledger.entity.FollowupExam;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;

/**
 * 随访检查 + 地形图 + 生物力学参数的数据访问。
 */
@Repository
@RequiredArgsConstructor
public class ExamDAO {

    private final JdbcTemplate jdbc;

    private static final String BASE_SELECT =
            "SELECT e.exam_id, e.patient_id, e.exam_date, e.exam_time, e.eye_side, e.exam_type, e.notes, "
          + "       e.risk_flag, e.stage_code, e.created_at, "
          + "       t.id AS t_id, t.front_k1, t.front_k2, t.front_km, t.front_kmax, t.front_rmin, t.front_q_val, "
          + "       t.front_axis, t.front_astig, t.front_qs, t.back_k1, t.back_k2, t.back_km, t.back_rmin, "
          + "       t.back_q_val, t.back_qs, t.pupil_center_pachy, t.pachy_apex, t.thinnest_pachy, "
          + "       t.cornea_volume, t.ac_depth, t.iop, "
          + "       b.id AS b_id, b.ccbi, b.ctbi, b.is_value, b.sp_a1, b.integr_radius, b.arth, b.da_ratio, b.ssi, "
          + "       (SELECT mi.image_path FROM medical_images mi WHERE mi.exam_id = e.exam_id "
          + "        ORDER BY mi.image_id DESC LIMIT 1) AS image_path "
          + "FROM followup_exams e "
          + "LEFT JOIN corneal_topography t ON t.exam_id = e.exam_id "
          + "LEFT JOIN biomechanical_params b ON b.exam_id = e.exam_id ";

    private static final RowMapper<FollowupExam> MAPPER = (rs, i) -> {
        FollowupExam e = new FollowupExam();
        e.setExamId(rs.getInt("exam_id"));
        e.setPatientId(rs.getString("patient_id"));
        Date d = rs.getDate("exam_date");
        e.setExamDate(d == null ? null : d.toLocalDate());
        Time time = rs.getTime("exam_time");
        e.setExamTime(time == null ? null : time.toLocalTime());
        e.setEyeSide(rs.getString("eye_side"));
        e.setExamType(rs.getString("exam_type"));
        e.setNotes(rs.getString("notes"));
        e.setRiskFlag(rs.getString("risk_flag"));
        e.setStageCode(rs.getString("stage_code"));
        Timestamp created = rs.getTimestamp("created_at");
        e.setCreatedAt(created == null ? null : created.toLocalDateTime());
        e.setImagePath(rs.getString("image_path"));

        Integer tId = (Integer) rs.getObject("t_id");
        if (tId != null) {
            CornealTopography topo = new CornealTopography();
            topo.setId(tId);
            topo.setExamId(e.getExamId());
            topo.setFrontK1(rs.getBigDecimal("front_k1"));
            topo.setFrontK2(rs.getBigDecimal("front_k2"));
            topo.setFrontKm(rs.getBigDecimal("front_km"));
            topo.setFrontKmax(rs.getBigDecimal("front_kmax"));
            topo.setFrontRmin(rs.getBigDecimal("front_rmin"));
            topo.setFrontQVal(rs.getBigDecimal("front_q_val"));
            topo.setFrontAxis(rs.getBigDecimal("front_axis"));
            topo.setFrontAstig(rs.getBigDecimal("front_astig"));
            topo.setFrontQs(rs.getString("front_qs"));
            topo.setBackK1(rs.getBigDecimal("back_k1"));
            topo.setBackK2(rs.getBigDecimal("back_k2"));
            topo.setBackKm(rs.getBigDecimal("back_km"));
            topo.setBackRmin(rs.getBigDecimal("back_rmin"));
            topo.setBackQVal(rs.getBigDecimal("back_q_val"));
            topo.setBackQs(rs.getString("back_qs"));
            topo.setPupilCenterPachy(rs.getBigDecimal("pupil_center_pachy"));
            topo.setPachyApex(rs.getBigDecimal("pachy_apex"));
            topo.setThinnestPachy(rs.getBigDecimal("thinnest_pachy"));
            topo.setCorneaVolume(rs.getBigDecimal("cornea_volume"));
            topo.setAcDepth(rs.getBigDecimal("ac_depth"));
            topo.setIop(rs.getBigDecimal("iop"));
            e.setTopography(topo);
        }

        Integer bId = (Integer) rs.getObject("b_id");
        if (bId != null) {
            BiomechanicalParams biomech = new BiomechanicalParams();
            biomech.setId(bId);
            biomech.setExamId(e.getExamId());
            biomech.setCcbi(rs.getBigDecimal("ccbi"));
            biomech.setCtbi(rs.getBigDecimal("ctbi"));
            biomech.setIsValue(rs.getBigDecimal("is_value"));
            biomech.setSpA1(rs.getBigDecimal("sp_a1"));
            biomech.setIntegrRadius(rs.getBigDecimal("integr_radius"));
            biomech.setArth(rs.getBigDecimal("arth"));
            biomech.setDaRatio(rs.getBigDecimal("da_ratio"));
            biomech.setSsi(rs.getBigDecimal("ssi"));
            e.setBiomechanics(biomech);
        }
        return e;
    };

    public List<FollowupExam> listByPatient(String patientId) {
        return jdbc.query(BASE_SELECT + "WHERE e.patient_id = ? ORDER BY e.exam_date, e.exam_time, e.eye_side",
                MAPPER, patientId);
    }

    public FollowupExam findById(int examId) {
        List<FollowupExam> list = jdbc.query(BASE_SELECT + "WHERE e.exam_id = ?", MAPPER, examId);
        return list.isEmpty() ? null : list.get(0);
    }

    public FollowupExam findLatest(String patientId, String eyeSide) {
        List<FollowupExam> list = jdbc.query(
                BASE_SELECT + "WHERE e.patient_id = ? AND e.eye_side = ? ORDER BY e.exam_date DESC, e.exam_time DESC LIMIT 1",
                MAPPER, patientId, eyeSide);
        return list.isEmpty() ? null : list.get(0);
    }

    public int insertExam(FollowupExam e) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO followup_exams (patient_id, exam_date, exam_time, eye_side, exam_type, notes, "
                            + "risk_flag, stage_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"exam_id"});
            ps.setString(1, e.getPatientId());
            ps.setDate(2, Date.valueOf(e.getExamDate()));
            ps.setTime(3, e.getExamTime() == null ? null : Time.valueOf(e.getExamTime()));
            ps.setString(4, e.getEyeSide());
            ps.setString(5, e.getExamType());
            ps.setString(6, e.getNotes());
            ps.setString(7, e.getRiskFlag() == null ? "NONE" : e.getRiskFlag());
            ps.setString(8, e.getStageCode());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        int id = key == null ? 0 : key.intValue();
        e.setExamId(id);
        return id;
    }

    public void insertTopography(CornealTopography t) {
        jdbc.update("INSERT INTO corneal_topography (exam_id, front_k1, front_k2, front_km, front_kmax, front_rmin, "
                        + "front_q_val, front_axis, front_astig, front_qs, back_k1, back_k2, back_km, back_rmin, "
                        + "back_q_val, back_qs, pupil_center_pachy, pachy_apex, thinnest_pachy, cornea_volume, "
                        + "ac_depth, iop) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                t.getExamId(), nz(t.getFrontK1()), nz(t.getFrontK2()), nz(t.getFrontKm()), nz(t.getFrontKmax()),
                nz(t.getFrontRmin()), nz(t.getFrontQVal()), nz(t.getFrontAxis()), nz(t.getFrontAstig()),
                t.getFrontQs(), nz(t.getBackK1()), nz(t.getBackK2()), nz(t.getBackKm()), nz(t.getBackRmin()),
                nz(t.getBackQVal()), t.getBackQs(), nz(t.getPupilCenterPachy()), nz(t.getPachyApex()),
                nz(t.getThinnestPachy()), nz(t.getCorneaVolume()), nz(t.getAcDepth()), nz(t.getIop()));
    }

    public void insertBiomechanics(BiomechanicalParams b) {
        jdbc.update("INSERT INTO biomechanical_params (exam_id, ccbi, ctbi, is_value, sp_a1, integr_radius, arth, "
                        + "da_ratio, ssi) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                b.getExamId(), nz(b.getCcbi()), nz(b.getCtbi()), nz(b.getIsValue()), nz(b.getSpA1()),
                nz(b.getIntegrRadius()), nz(b.getArth()), nz(b.getDaRatio()), nz(b.getSsi()));
    }

    public void updateRiskAndStage(int examId, String riskFlag, String stageCode) {
        jdbc.update("UPDATE followup_exams SET risk_flag=?, stage_code=? WHERE exam_id=?",
                riskFlag, stageCode, examId);
    }

    public void delete(int examId) {
        jdbc.update("DELETE FROM followup_exams WHERE exam_id=?", examId);
    }

    /** 统计某患者指定眼别的检查次数（用于进展判定）。 */
    public int countByPatientAndEye(String patientId, String eyeSide) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM followup_exams WHERE patient_id=? AND eye_side=?",
                Integer.class, patientId, eyeSide);
        return n == null ? 0 : n;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v;
    }
}
