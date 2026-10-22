package com.eyefollowup.ledger.dao;

import com.eyefollowup.ledger.entity.MedicalImage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

/**
 * 医学影像元数据访问。
 */
@Repository
@RequiredArgsConstructor
public class ImageDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<MedicalImage> MAPPER = (rs, i) -> {
        MedicalImage m = new MedicalImage();
        m.setImageId(rs.getInt("image_id"));
        m.setPatientId(rs.getString("patient_id"));
        m.setExamId((Integer) rs.getObject("exam_id"));
        m.setImageType(rs.getString("image_type"));
        m.setEyeSide(rs.getString("eye_side"));
        m.setImagePath(rs.getString("image_path"));
        m.setFileName(rs.getString("file_name"));
        m.setFileSize((Long) rs.getObject("file_size"));
        m.setOcrJson(rs.getString("ocr_json"));
        m.setReportType(rs.getString("report_type"));
        m.setOcrConfidence(rs.getBigDecimal("ocr_confidence"));
        Timestamp t = rs.getTimestamp("upload_date");
        m.setUploadDate(t == null ? null : t.toLocalDateTime());
        return m;
    };

    public int insert(MedicalImage m) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO medical_images (patient_id, exam_id, image_type, eye_side, image_path, file_name, "
                            + "file_size, ocr_json, report_type, ocr_confidence) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"image_id"});
            ps.setString(1, m.getPatientId());
            if (m.getExamId() == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, m.getExamId());
            ps.setString(3, m.getImageType() == null ? "Pentacam" : m.getImageType());
            ps.setString(4, m.getEyeSide());
            ps.setString(5, m.getImagePath());
            ps.setString(6, m.getFileName());
            if (m.getFileSize() == null) ps.setNull(7, java.sql.Types.BIGINT); else ps.setLong(7, m.getFileSize());
            ps.setString(8, m.getOcrJson());
            ps.setString(9, m.getReportType());
            if (m.getOcrConfidence() == null) ps.setNull(10, java.sql.Types.DECIMAL); else ps.setBigDecimal(10, m.getOcrConfidence());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        int id = key == null ? 0 : key.intValue();
        m.setImageId(id);
        return id;
    }

    public MedicalImage findById(int imageId) {
        List<MedicalImage> list = jdbc.query("SELECT * FROM medical_images WHERE image_id=?", MAPPER, imageId);
        return list.isEmpty() ? null : list.get(0);
    }

    public void attach(int imageId, String patientId, Integer examId, String eyeSide,
                       String ocrJson, String reportType, BigDecimal ocrConfidence) {
        jdbc.update("UPDATE medical_images SET patient_id=?, exam_id=?, eye_side=?, ocr_json=?, report_type=?, "
                        + "ocr_confidence=? WHERE image_id=?",
                patientId, examId, eyeSide, ocrJson, reportType, ocrConfidence, imageId);
    }

    public List<MedicalImage> listByPatient(String patientId) {
        return jdbc.query("SELECT * FROM medical_images WHERE patient_id=? ORDER BY upload_date DESC",
                MAPPER, patientId);
    }

    public List<MedicalImage> listByExam(int examId) {
        return jdbc.query("SELECT * FROM medical_images WHERE exam_id=? ORDER BY image_id DESC", MAPPER, examId);
    }
}
