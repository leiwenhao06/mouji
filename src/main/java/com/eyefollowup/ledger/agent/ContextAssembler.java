package com.eyefollowup.ledger.agent;

import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.dao.ExamDAO;
import com.eyefollowup.ledger.dao.PatientDAO;
import com.eyefollowup.ledger.dao.PlanDAO;
import com.eyefollowup.ledger.entity.FollowupExam;
import com.eyefollowup.ledger.entity.Patient;
import com.eyefollowup.ledger.service.PlanService;
import com.eyefollowup.ledger.service.ProgressionDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 上下文组装：患者临床数据 → 脱敏的 Agent 输入。
 */
@Component
@RequiredArgsConstructor
public class ContextAssembler {

    private final PatientDAO patientDAO;
    private final ExamDAO examDAO;
    private final PlanDAO planDAO;
    private final ProgressionDetector detector;

    public FollowupContext assemble(String patientId) {
        Patient patient = patientDAO.findById(patientId);
        if (patient == null) {
            throw new BizException(404, "患者不存在");
        }
        List<FollowupExam> exams = examDAO.listByPatient(patientId);
        if (exams.isEmpty()) {
            throw new BizException("该患者尚无检查记录，无法生成随访建议");
        }

        FollowupContext ctx = FollowupContext.from(patient);
        ctx.setExamCount(exams.size());
        ctx.setPlan(planDAO.findActive(patientId));

        List<FollowupExam> sorted = exams.stream()
                .sorted(Comparator.comparing(FollowupExam::getExamDate)
                        .thenComparing(e -> e.getExamTime() == null ? java.time.LocalTime.MIN : e.getExamTime()))
                .collect(Collectors.toList());
        ctx.setLatestExam(sorted.get(sorted.size() - 1));
        ctx.setLatestRight(lastOfEye(sorted, "right"));
        ctx.setLatestLeft(lastOfEye(sorted, "left"));

        // 最近 6 次检查（时间倒序）
        List<FollowupExam> recent = new ArrayList<>(sorted);
        java.util.Collections.reverse(recent);
        ctx.setRecentExams(recent.stream().limit(6).collect(Collectors.toList()));

        // 对每只眼分别检测，取风险更高的一侧作为总体结论
        ProgressionDetector.Result worst = null;
        List<String> basisParts = new ArrayList<>();
        if (ctx.getLatestRight() != null) {
            ProgressionDetector.Result r = detect(ctx.getLatestRight(), sorted, "右眼");
            worst = worse(worst, r);
            basisParts.add(r.getBasis());
        }
        if (ctx.getLatestLeft() != null) {
            ProgressionDetector.Result r = detect(ctx.getLatestLeft(), sorted, "左眼");
            worst = worse(worst, r);
            basisParts.add(r.getBasis());
        }
        if (worst == null) {
            worst = detect(ctx.getLatestExam(), sorted, "本次");
            basisParts.add(worst.getBasis());
        }

        ctx.setStageCode(worst.getStageCode());
        ctx.setRiskFlag(worst.getRiskFlag());
        ctx.setStageLabel(PlanService.stageLabel(worst.getStageCode()));
        ctx.setRiskLabel(PlanService.riskLabel(worst.getRiskFlag()));
        ctx.setDetectionBasis(String.join("；", basisParts));
        ctx.setDetectionConfidence(worst.getConfidence());
        ctx.setPostCxl("POST_CXL".equals(worst.getStageCode())
                || sorted.stream().anyMatch(e -> "POST_CXL".equals(e.getStageCode())));
        return ctx;
    }

    private FollowupExam lastOfEye(List<FollowupExam> sorted, String eye) {
        FollowupExam found = null;
        for (FollowupExam e : sorted) {
            if (eye.equals(e.getEyeSide()) || "both".equals(e.getEyeSide())) {
                found = e;
            }
        }
        return found;
    }

    private ProgressionDetector.Result detect(FollowupExam exam, List<FollowupExam> all, String label) {
        List<FollowupExam> history = all.stream()
                .filter(e -> e.getExamId() != null && !e.getExamId().equals(exam.getExamId()))
                .filter(e -> exam.getEyeSide().equals(e.getEyeSide()) || "both".equals(e.getEyeSide()))
                .collect(Collectors.toList());
        ProgressionDetector.Result r = detector.detect(exam, history);
        return new ProgressionDetector.Result(r.getStageCode(), r.getRiskFlag(),
                label + " " + r.getBasis(), r.getConfidence());
    }

    /** 风险优先级：SEVERE > PROGRESSION > SUSPECT > NONE。 */
    private ProgressionDetector.Result worse(ProgressionDetector.Result a, ProgressionDetector.Result b) {
        if (a == null) return b;
        if (b == null) return a;
        return riskRank(b.getRiskFlag()) > riskRank(a.getRiskFlag()) ? b : a;
    }

    private int riskRank(String risk) {
        return switch (risk == null ? "NONE" : risk) {
            case "SEVERE" -> 3;
            case "PROGRESSION" -> 2;
            case "SUSPECT" -> 1;
            default -> 0;
        };
    }
}
