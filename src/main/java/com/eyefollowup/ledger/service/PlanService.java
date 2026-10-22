package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.dao.PatientDAO;
import com.eyefollowup.ledger.dao.PlanDAO;
import com.eyefollowup.ledger.entity.FollowupPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 随访计划：新增检查后自动按分期/进展调整频率；每日刷新逾期状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanDAO planDAO;
    private final PatientDAO patientDAO;

    /**
     * 根据最新检查结果自动生成/调整随访计划。
     * 频率：进展/重度/交联术后 3 个月；临床期/可疑 6 个月；稳定 12 个月。
     */
    public FollowupPlan autoAdjust(String patientId, LocalDate examDate, ProgressionDetector.Result result) {
        int frequency = frequencyFor(result);
        FollowupPlan active = planDAO.findActive(patientId);

        FollowupPlan plan = active == null ? new FollowupPlan() : active;
        plan.setPatientId(patientId);
        plan.setStageCode(result.getStageCode());
        plan.setFrequencyMonths(frequency);
        plan.setLastExamDate(examDate);
        plan.setNextDueDate(examDate.plusMonths(frequency));
        plan.setStatus("PENDING");
        plan.setBasis(buildBasis(result, frequency));

        if (active == null) {
            planDAO.insert(plan);
        } else {
            planDAO.update(plan);
        }
        return plan;
    }

    private int frequencyFor(ProgressionDetector.Result r) {
        if ("PROGRESSION".equals(r.getRiskFlag()) || "SEVERE".equals(r.getRiskFlag())
                || "POST_CXL".equals(r.getStageCode())) {
            return 3;
        }
        if ("CLINICAL".equals(r.getStageCode()) || "ADVANCED".equals(r.getStageCode())
                || "SUSPECT".equals(r.getStageCode())) {
            return 6;
        }
        return 12;
    }

    private String buildBasis(ProgressionDetector.Result r, int frequency) {
        String basis = "分期：" + stageLabel(r.getStageCode())
                + "；风险：" + riskLabel(r.getRiskFlag())
                + "；建议每 " + frequency + " 个月复查。依据：" + r.getBasis();
        return basis.length() > 490 ? basis.substring(0, 490) : basis;
    }

    public static String stageLabel(String code) {
        if (code == null) return "未分期";
        return switch (code) {
            case "NORMAL" -> "稳定/正常";
            case "SUSPECT" -> "可疑";
            case "CLINICAL" -> "临床期";
            case "ADVANCED" -> "重度";
            case "POST_CXL" -> "交联术后";
            default -> "未分期";
        };
    }

    public static String riskLabel(String code) {
        if (code == null) return "无";
        return switch (code) {
            case "PROGRESSION" -> "进展";
            case "SEVERE" -> "重度风险";
            case "SUSPECT" -> "需关注";
            default -> "无";
        };
    }

    public List<FollowupPlan> list(String status, String keyword) {
        planDAO.markOverdue();
        return planDAO.list(status, keyword, 200);
    }

    public FollowupPlan getActive(String patientId) {
        // 读取时顺带刷新逾期状态，保证台账页与列表页口径一致
        planDAO.markOverdue();
        return planDAO.findActive(patientId);
    }

    public FollowupPlan createManual(String patientId, int frequencyMonths, LocalDate nextDueDate, String basis) {
        if (patientDAO.findById(patientId) == null) {
            throw new BizException(404, "患者不存在");
        }
        FollowupPlan plan = new FollowupPlan();
        plan.setPatientId(patientId);
        plan.setFrequencyMonths(Math.max(1, Math.min(frequencyMonths, 24)));
        plan.setNextDueDate(nextDueDate);
        plan.setStatus("PENDING");
        plan.setBasis(basis == null || basis.isBlank() ? "医生手工制定" : basis);
        planDAO.insert(plan);
        return plan;
    }

    public void close(int planId) {
        checkExists(planId);
        planDAO.updateStatus(planId, "CLOSED");
    }

    public void done(int planId) {
        checkExists(planId);
        planDAO.updateStatus(planId, "DONE");
    }

    public long overdueCount() {
        planDAO.markOverdue();
        return planDAO.countOverdue();
    }

    private void checkExists(int planId) {
        if (planDAO.findById(planId) == null) {
            throw new BizException(404, "随访计划不存在");
        }
    }

    @Scheduled(cron = "0 10 8 * * *")
    public void scheduledMarkOverdue() {
        int n = planDAO.markOverdue();
        if (n > 0) {
            log.info("已将 {} 条随访计划标记为逾期", n);
        }
    }
}
