package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.dao.ExamDAO;
import com.eyefollowup.ledger.dao.PatientDAO;
import com.eyefollowup.ledger.entity.BiomechanicalParams;
import com.eyefollowup.ledger.entity.CornealTopography;
import com.eyefollowup.ledger.entity.FollowupExam;
import com.eyefollowup.ledger.entity.FollowupPlan;
import com.eyefollowup.ledger.entity.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 台账导出：UTF-8 带 BOM 的 CSV，Excel 可直接打开。
 * 仅导出随访台账字段，不含任何诊断结论。
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final PatientDAO patientDAO;
    private final ExamDAO examDAO;
    private final PlanService planService;
    private final AuditService auditService;

    /** 全员汇总台账：患者 + 最近一次检查关键指标 + 当前随访计划。 */
    public CsvFile exportPatientSummary() {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("患者ID,姓名,性别,年龄,出生日期,联系电话,检查次数,最近检查,最新KMax(D),最新最薄点(μm),分期,风险,计划状态,下次随访\r\n");
        List<Patient> patients = patientDAO.page(null, null, 0, 10000);
        for (Patient p : patients) {
            List<FollowupExam> exams = examDAO.listByPatient(p.getPatientId());
            FollowupExam latest = exams.isEmpty() ? null : exams.get(exams.size() - 1);
            FollowupPlan plan = planService.getActive(p.getPatientId());
            CornealTopography t = latest == null ? null : latest.getTopography();
            row(sb,
                    p.getPatientId(),
                    p.getName(),
                    genderLabel(p.getGender()),
                    p.getAge() == null ? "" : p.getAge().toString(),
                    p.getDateOfBirth() == null ? "" : p.getDateOfBirth().toString(),
                    p.getContact(),
                    String.valueOf(exams.size()),
                    latest == null ? "" : latest.getExamDate().toString(),
                    num(t == null ? null : t.getFrontKmax()),
                    num(t == null ? null : t.getThinnestPachy()),
                    stageLabel(latest == null ? null : latest.getStageCode()),
                    riskLabel(latest == null ? null : latest.getRiskFlag()),
                    planStatusLabel(plan == null ? null : plan.getStatus()),
                    plan == null || plan.getNextDueDate() == null ? "" : plan.getNextDueDate().toString());
        }
        auditService.log("EXPORT", "PATIENT_SUMMARY", "导出全员汇总台账（" + patients.size() + " 人）", null);
        return new CsvFile("随访台账汇总.csv", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 单患者检查明细台账。 */
    public CsvFile exportPatientLedger(String patientId) {
        Patient p = patientDAO.findById(patientId);
        if (p == null) {
            throw new BizException(404, "患者不存在");
        }
        List<FollowupExam> exams = examDAO.listByPatient(patientId);
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("检查日期,检查时间,眼别,检查类型,K1(D),K2(D),Km(D),KMax(D),前表面Rmin(mm),最薄点厚度(μm),角膜容积,前房深度(mm),眼压(mmHg),cTBI,分期,风险标记,备注\r\n");
        for (FollowupExam e : exams) {
            CornealTopography t = e.getTopography();
            BiomechanicalParams b = e.getBiomechanics();
            row(sb,
                    e.getExamDate() == null ? "" : e.getExamDate().toString(),
                    e.getExamTime() == null ? "" : e.getExamTime().toString(),
                    eyeLabel(e.getEyeSide()),
                    e.getExamType(),
                    num(t == null ? null : t.getFrontK1()),
                    num(t == null ? null : t.getFrontK2()),
                    num(t == null ? null : t.getFrontKm()),
                    num(t == null ? null : t.getFrontKmax()),
                    num(t == null ? null : t.getFrontRmin()),
                    num(t == null ? null : t.getThinnestPachy()),
                    num(t == null ? null : t.getCorneaVolume()),
                    num(t == null ? null : t.getAcDepth()),
                    num(t == null ? null : t.getIop()),
                    num(b == null ? null : b.getCtbi()),
                    stageLabel(e.getStageCode()),
                    riskLabel(e.getRiskFlag()),
                    e.getNotes());
        }
        auditService.log("EXPORT", "PATIENT_LEDGER", "导出检查明细台账（" + exams.size() + " 条）", patientId);
        return new CsvFile("随访台账-" + p.getName() + ".csv", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append("\r\n");
    }

    private String escape(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    private String num(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private String genderLabel(String g) {
        return "Male".equals(g) ? "男" : "Female".equals(g) ? "女" : (g == null ? "" : g);
    }

    private String eyeLabel(String eye) {
        return "left".equals(eye) ? "左眼" : "right".equals(eye) ? "右眼" : "both".equals(eye) ? "双眼" : (eye == null ? "" : eye);
    }

    private String stageLabel(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "NORMAL" -> "稳定/正常";
            case "SUSPECT" -> "可疑";
            case "CLINICAL" -> "临床期";
            case "ADVANCED" -> "重度";
            case "POST_CXL" -> "交联术后";
            default -> code;
        };
    }

    private String riskLabel(String flag) {
        if (flag == null) {
            return "";
        }
        return switch (flag) {
            case "NONE" -> "无";
            case "SUSPECT" -> "需关注";
            case "PROGRESSION" -> "进展";
            case "SEVERE" -> "重度风险";
            default -> flag;
        };
    }

    private String planStatusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "PENDING" -> "待随访";
            case "OVERDUE" -> "已逾期";
            case "DONE" -> "已随访";
            case "CLOSED" -> "已关闭";
            default -> status;
        };
    }

    public record CsvFile(String filename, byte[] content) {
    }
}
