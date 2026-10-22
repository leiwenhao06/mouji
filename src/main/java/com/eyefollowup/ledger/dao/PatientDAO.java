package com.eyefollowup.ledger.dao;

import com.eyefollowup.ledger.entity.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 患者数据访问。全部使用参数化 SQL。
 */
@Repository
@RequiredArgsConstructor
public class PatientDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Patient> MAPPER = (rs, i) -> {
        Patient p = new Patient();
        p.setPatientId(rs.getString("patient_id"));
        p.setName(rs.getString("name"));
        p.setGender(rs.getString("gender"));
        Date dob = rs.getDate("date_of_birth");
        p.setDateOfBirth(dob == null ? null : dob.toLocalDate());
        p.setAge((Integer) rs.getObject("age"));
        p.setContact(rs.getString("contact"));
        p.setMedicalHistory(rs.getString("medical_history"));
        Timestamp created = rs.getTimestamp("created_at");
        p.setCreatedAt(created == null ? null : created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        p.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        return p;
    };

    public long count(String keyword, String gender) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM patients WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, keyword, gender);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<Patient> page(String keyword, String gender, int offset, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM patients WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, keyword, gender);
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    private void appendFilter(StringBuilder sql, List<Object> args, String keyword, String gender) {
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (gender != null && !gender.isBlank()) {
            sql.append(" AND gender = ?");
            args.add(gender);
        }
    }

    public Patient findById(String patientId) {
        List<Patient> list = jdbc.query("SELECT * FROM patients WHERE patient_id = ?", MAPPER, patientId);
        return list.isEmpty() ? null : list.get(0);
    }

    public Patient findByNameAndBirth(String name, LocalDate dob) {
        List<Patient> list = jdbc.query(
                "SELECT * FROM patients WHERE name = ? AND date_of_birth = ? LIMIT 1",
                MAPPER, name, dob == null ? null : Date.valueOf(dob));
        return list.isEmpty() ? null : list.get(0);
    }

    public void insert(Patient p) {
        jdbc.update("INSERT INTO patients (patient_id, name, gender, date_of_birth, age, contact, medical_history) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                p.getPatientId(), p.getName(), p.getGender(),
                p.getDateOfBirth() == null ? null : Date.valueOf(p.getDateOfBirth()),
                p.getAge(), p.getContact(), p.getMedicalHistory());
    }

    public void update(Patient p) {
        jdbc.update("UPDATE patients SET name=?, gender=?, date_of_birth=?, age=?, contact=?, medical_history=? "
                        + "WHERE patient_id=?",
                p.getName(), p.getGender(),
                p.getDateOfBirth() == null ? null : Date.valueOf(p.getDateOfBirth()),
                p.getAge(), p.getContact(), p.getMedicalHistory(), p.getPatientId());
    }

    public void updateAge(String patientId, int age) {
        jdbc.update("UPDATE patients SET age=? WHERE patient_id=?", age, patientId);
    }

    public void delete(String patientId) {
        jdbc.update("DELETE FROM patients WHERE patient_id=?", patientId);
    }
}
