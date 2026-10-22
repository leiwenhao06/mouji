package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.entity.CornealTopography;
import com.eyefollowup.ledger.entity.FollowupExam;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 进展检测引擎（确定性规则，可单测）。
 *
 * 分期阈值（参考中国圆锥角膜专家共识与常用临床分期口径）：
 *  - KMax ≥ 47.2D：临床期；46.0~47.1D：可疑
 *  - 最薄点厚度 < 400μm：重度风险
 *  - 后表面 Rmin < 5.0mm 或 cTBI ≥ 0.8：可疑/亚临床提示
 * 进展判定（与至少 3 个月前的一次检查对比，年化）：
 *  - KMax 年化升幅 ≥ 1.0D，或最薄点厚度年化下降 ≥ 10μm
 */
@Service
public class ProgressionDetector {

    public Result detect(FollowupExam current, List<FollowupExam> historySameEye) {
        List<String> basis = new ArrayList<>();
        CornealTopography topo = current.getTopography();
        if (topo == null || topo.getFrontKmax() == null) {
            return new Result("UNKNOWN", "NONE", "缺少角膜地形图关键参数，无法自动分期", "low");
        }

        double kmax = topo.getFrontKmax().doubleValue();
        Double thinnest = topo.getThinnestPachy() == null ? null : topo.getThinnestPachy().doubleValue();
        Double backRmin = topo.getBackRmin() == null ? null : topo.getBackRmin().doubleValue();
        Double ctbi = current.getBiomechanics() == null || current.getBiomechanics().getCtbi() == null
                ? null : current.getBiomechanics().getCtbi().doubleValue();

        boolean postCxl = isPostCxl(current, historySameEye);

        String stage;
        if (postCxl) {
            stage = "POST_CXL";
        } else if ((thinnest != null && thinnest < 400) || kmax >= 52.0) {
            stage = "ADVANCED";
        } else if (kmax >= 47.2) {
            stage = "CLINICAL";
        } else if (kmax >= 46.0 || (backRmin != null && backRmin < 5.0)
                || (ctbi != null && ctbi >= 0.8)) {
            stage = "SUSPECT";
        } else {
            stage = "NORMAL";
        }

        basis.add(String.format("KMax %.1fD", kmax));
        if (thinnest != null) {
            basis.add(String.format("最薄点厚度 %.0fμm", thinnest));
        }
        if (backRmin != null) {
            basis.add(String.format("后表面Rmin %.2fmm", backRmin));
        }
        if (ctbi != null) {
            basis.add(String.format("cTBI %.2f", ctbi));
        }

        // ---- 进展判定：选择 60 天以前、日期最接近当前的历史检查作为基线 ----
        FollowupExam baseline = null;
        for (FollowupExam h : historySameEye) {
            if (h.getExamDate() == null || h.getTopography() == null
                    || h.getTopography().getFrontKmax() == null) {
                continue;
            }
            if (!h.getExamDate().isBefore(current.getExamDate().minusDays(60))) {
                continue;
            }
            if (baseline == null || h.getExamDate().isAfter(baseline.getExamDate())) {
                baseline = h;
            }
        }

        boolean progression = false;
        if (baseline != null) {
            long days = ChronoUnit.DAYS.between(baseline.getExamDate(), current.getExamDate());
            if (days >= 90) {
                double months = days / 30.4375;
                double baseKmax = baseline.getTopography().getFrontKmax().doubleValue();
                double annualK = (kmax - baseKmax) * 12.0 / months;
                if (annualK >= 1.0) {
                    progression = true;
                    basis.add(String.format("KMax 年化升幅 %.2fD（≥1.0D）", annualK));
                }
                if (thinnest != null && baseline.getTopography().getThinnestPachy() != null) {
                    double baseThin = baseline.getTopography().getThinnestPachy().doubleValue();
                    double annualThin = (baseThin - thinnest) * 12.0 / months;
                    if (annualThin >= 10.0) {
                        progression = true;
                        basis.add(String.format("最薄点厚度年化下降 %.1fμm（≥10μm）", annualThin));
                    }
                }
                basis.add(String.format("对比基线 %s（间隔约 %.1f 个月）",
                        baseline.getExamDate(), BigDecimal.valueOf(months).setScale(1, RoundingMode.HALF_UP)));
            }
        }

        String risk;
        if (thinnest != null && thinnest < 400) {
            risk = "SEVERE";
            basis.add("最薄点厚度 < 400μm，属重度风险");
        } else if (progression) {
            risk = "PROGRESSION";
        } else if (!"NORMAL".equals(stage) && !"UNKNOWN".equals(stage)) {
            risk = "SUSPECT";
        } else {
            risk = "NONE";
        }

        String confidence = baseline != null ? "high" : "medium";
        return new Result(stage, risk, String.join("；", basis), confidence);
    }

    private boolean isPostCxl(FollowupExam current, List<FollowupExam> history) {
        if (current.getNotes() != null
                && (current.getNotes().contains("交联") || current.getNotes().contains("术后"))) {
            return true;
        }
        return history.stream().anyMatch(h -> "POST_CXL".equals(h.getStageCode()));
    }

    @Data
    @AllArgsConstructor
    public static class Result {
        /** NORMAL / SUSPECT / CLINICAL / ADVANCED / POST_CXL / UNKNOWN */
        private String stageCode;
        /** NONE / SUSPECT / PROGRESSION / SEVERE */
        private String riskFlag;
        private String basis;
        private String confidence;
    }
}
