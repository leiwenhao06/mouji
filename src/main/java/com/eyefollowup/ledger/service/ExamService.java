package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.dao.ExamDAO;
import com.eyefollowup.ledger.dao.PatientDAO;
import com.eyefollowup.ledger.dto.ExamRequest;
import com.eyefollowup.ledger.entity.BiomechanicalParams;
import com.eyefollowup.ledger.entity.CornealTopography;
import com.eyefollowup.ledger.entity.FollowupExam;
import com.eyefollowup.ledger.entity.FollowupPlan;
import com.eyefollowup.ledger.entity.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 随访检查：入库后自动跑进展检测并调整随访计划。
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamDAO examDAO;
    private final PatientDAO patientDAO;
    private final ProgressionDetector progressionDetector;
    private final PlanService planService;
    private final AuditService auditService;

    @Transactional
    public FollowupExam create(ExamRequest req) {
        Patient patient = patientDAO.findById(req.getPatientId());
        if (patient == null) {
            throw new BizException(404, "患者不存在");
        }
        if (!List.of("left", "right", "both").contains(req.getEyeSide())) {
            throw new BizException("眼别取值不合法");
        }

        // 历史同眼检查（用于进展对比），必须在插入新记录之前读取
        List<FollowupExam> history = examDAO.listByPatient(req.getPatientId()).stream()
                .filter(e -> req.getEyeSide().equals(e.getEyeSide()))
                .collect(Collectors.toList());

        FollowupExam exam = new FollowupExam();
        exam.setPatientId(req.getPatientId());
        exam.setExamDate(req.getExamDate());
        exam.setExamTime(req.getExamTime());
        exam.setEyeSide(req.getEyeSide());
        exam.setExamType(req.getExamType() == null ? "Pentacam" : req.getExamType());
        exam.setNotes(req.getNotes());
        int examId = examDAO.insertExam(exam);

        if (req.getTopography() != null) {
            CornealTopography t = req.getTopography();
            t.setExamId(examId);
            examDAO.insertTopography(t);
            exam.setTopography(t);
        }
        if (req.getBiomechanics() != null) {
            BiomechanicalParams b = req.getBiomechanics();
            b.setExamId(examId);
            examDAO.insertBiomechanics(b);
            exam.setBiomechanics(b);
        }

        ProgressionDetector.Result result = progressionDetector.detect(exam, history);
        examDAO.updateRiskAndStage(examId, result.getRiskFlag(), result.getStageCode());
        exam.setRiskFlag(result.getRiskFlag());
        exam.setStageCode(result.getStageCode());

        FollowupPlan plan = planService.autoAdjust(req.getPatientId(), req.getExamDate(), result);
        exam.setNotes(exam.getNotes());

        auditService.log("EXAM", "CREATE",
                "新增检查 #" + examId + "（" + req.getEyeSide() + "），风险=" + result.getRiskFlag()
                        + "，下次随访=" + plan.getNextDueDate(), req.getPatientId());
        return exam;
    }

    public FollowupExam get(int examId) {
        FollowupExam exam = examDAO.findById(examId);
        if (exam == null) {
            throw new BizException(404, "检查记录不存在");
        }
        return exam;
    }

    public List<FollowupExam> listByPatient(String patientId) {
        return examDAO.listByPatient(patientId);
    }

    public Map<String, Object> timeline(String patientId) {
        Patient patient = patientDAO.findById(patientId);
        if (patient == null) {
            throw new BizException(404, "患者不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patient", patient);
        result.put("exams", examDAO.listByPatient(patientId));
        result.put("plan", planService.getActive(patientId));
        return result;
    }

    @Transactional
    public void delete(int examId) {
        FollowupExam exam = get(examId);
        examDAO.delete(examId);
        auditService.log("EXAM", "DELETE", "删除检查 #" + examId, exam.getPatientId());
    }

    /** 供数据生成器/导入使用：判断患者最近一次检查日期。 */
    public LocalDate latestExamDate(String patientId) {
        List<FollowupExam> exams = examDAO.listByPatient(patientId);
        return exams.isEmpty() ? null : exams.get(exams.size() - 1).getExamDate();
    }
}
