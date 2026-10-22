package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.dao.PatientDAO;
import com.eyefollowup.ledger.dto.PatientRequest;
import com.eyefollowup.ledger.entity.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 患者管理。
 */
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientDAO patientDAO;
    private final AuditService auditService;

    public Map<String, Object> list(String keyword, String gender, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        long total = patientDAO.count(keyword, gender);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", patientDAO.page(keyword, gender, (safePage - 1) * safeSize, safeSize));
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    public Patient get(String patientId) {
        Patient p = patientDAO.findById(patientId);
        if (p == null) {
            throw new BizException(404, "患者不存在");
        }
        return p;
    }

    public Patient create(PatientRequest req) {
        Patient p = new Patient();
        p.setPatientId(UUID.randomUUID().toString().replace("-", ""));
        p.setName(req.getName().trim());
        p.setGender(req.getGender());
        p.setDateOfBirth(req.getDateOfBirth());
        p.setAge(calcAge(req.getDateOfBirth()));
        p.setContact(req.getContact());
        p.setMedicalHistory(req.getMedicalHistory());
        patientDAO.insert(p);
        auditService.log("PATIENT", "CREATE", "新建患者 " + p.getName(), p.getPatientId());
        return p;
    }

    public Patient update(String patientId, PatientRequest req) {
        Patient p = get(patientId);
        p.setName(req.getName().trim());
        p.setGender(req.getGender());
        p.setDateOfBirth(req.getDateOfBirth());
        p.setAge(calcAge(req.getDateOfBirth()));
        p.setContact(req.getContact());
        p.setMedicalHistory(req.getMedicalHistory());
        patientDAO.update(p);
        auditService.log("PATIENT", "UPDATE", "编辑患者 " + p.getName(), patientId);
        return p;
    }

    public void delete(String patientId) {
        get(patientId);
        patientDAO.delete(patientId);
        auditService.log("PATIENT", "DELETE", "删除患者 " + patientId, patientId);
    }

    public static Integer calcAge(LocalDate dob) {
        if (dob == null) {
            return null;
        }
        int age = Period.between(dob, LocalDate.now()).getYears();
        return Math.max(0, age);
    }
}
