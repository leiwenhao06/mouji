package com.eyefollowup.ledger.agent;

import com.eyefollowup.ledger.dto.FollowupAdvice;
import com.eyefollowup.ledger.entity.CornealTopography;
import com.eyefollowup.ledger.entity.FollowupExam;
import com.eyefollowup.ledger.entity.LocalKnowledge;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 循证规则草稿：确定性、可溯源、毫秒级。
 * 它是 Agent 流水线的骨架；大模型只做个体化补充，指南守卫以本结果为兜底。
 *
 * 合规红线：不输出药物剂量/疗程，不做手术决策，急症首条提示就医。
 */
@Component
public class RuleBasedFollowupRecommender {

    public static final String DISCLAIMER =
            "本建议由系统基于公开指南摘要与随访数据自动生成，仅用于随访管理参考，不构成诊断或治疗意见；"
          + "具体复查安排与诊疗方案请由眼科医师结合临床评估决定。";

    public FollowupAdvice build(FollowupContext ctx, List<LocalKnowledge> knowledge) {
        FollowupAdvice advice = new FollowupAdvice();

        // 1) 分期评估
        FollowupAdvice.StageAssessment stage = advice.getStageAssessment();
        String stageText = ctx.getStageLabel() == null ? "未分期" : ctx.getStageLabel();
        if (ctx.isPostCxl() && !stageText.contains("交联")) {
            stageText += "（交联术后）";
        }
        stage.setStage(stageText);
        stage.setBasis(splitBasis(ctx.getDetectionBasis()));
        stage.setConfidence(confidenceText(ctx.getDetectionConfidence()));

        // 2) 摘要
        advice.setSummary(buildSummary(ctx));

        // 3) 随访计划
        FollowupAdvice.FollowUpPlan plan = advice.getFollowUpPlan();
        plan.setFrequency(frequencyText(ctx));
        plan.setItems(followUpItems(ctx));
        plan.setWarningSigns(warningSigns());

        // 4) 生活与自我管理建议
        advice.setLifestyleAdvice(lifestyleAdvice(ctx));

        // 5) 危险信号（急症优先）
        advice.setRedFlags(redFlags(ctx));

        // 6) 参考依据
        advice.setKnowledgeRefs(knowledge.stream().map(k -> {
            FollowupAdvice.KnowledgeRef ref = new FollowupAdvice.KnowledgeRef();
            ref.setTitle(k.getTitle());
            ref.setSource(k.getSource());
            ref.setYear(k.getPubYear());
            return ref;
        }).collect(Collectors.toList()));

        advice.setDisclaimer(DISCLAIMER);
        return advice;
    }

    private List<String> splitBasis(String basis) {
        if (basis == null || basis.isBlank()) {
            return List.of("暂无足够的历史检查数据用于对比");
        }
        List<String> parts = new ArrayList<>();
        for (String p : basis.split("；")) {
            if (!p.isBlank()) {
                parts.add(p.trim());
            }
            if (parts.size() >= 8) break;
        }
        return parts;
    }

    private String confidenceText(String confidence) {
        if ("high".equals(confidence)) return "高（有历史检查对比）";
        if ("medium".equals(confidence)) return "中（基于单次检查）";
        return "低（有效参数不足）";
    }

    private String buildSummary(FollowupContext ctx) {
        FollowupExam exam = ctx.getLatestExam();
        StringBuilder sb = new StringBuilder();
        if (exam != null && exam.getTopography() != null) {
            CornealTopography t = exam.getTopography();
            String eye = "left".equals(exam.getEyeSide()) ? "左眼" : "右眼";
            sb.append("最近一次检查（").append(eye).append("）");
            List<String> metrics = new ArrayList<>();
            if (t.getFrontKmax() != null) metrics.add("KMax " + fmt(t.getFrontKmax(), 1) + "D");
            if (t.getThinnestPachy() != null) metrics.add("最薄点厚度 " + fmt(t.getThinnestPachy(), 0) + "μm");
            if (exam.getBiomechanics() != null && exam.getBiomechanics().getCtbi() != null) {
                metrics.add("cTBI " + fmt(exam.getBiomechanics().getCtbi(), 2));
            }
            sb.append(String.join("、", metrics)).append("。");
        }
        sb.append("当前评估为").append(ctx.getStageLabel());
        if ("PROGRESSION".equals(ctx.getRiskFlag())) {
            if ("NORMAL".equals(ctx.getStageCode()) || "SUSPECT".equals(ctx.getStageCode())) {
                sb.append("，尚未达到临床期，但检测到进展提示");
            } else {
                sb.append("，存在进展提示");
            }
        } else if ("SEVERE".equals(ctx.getRiskFlag())) {
            sb.append("，属重度风险");
        } else if ("SUSPECT".equals(ctx.getRiskFlag())) {
            sb.append("，需定期关注");
        } else {
            sb.append("，未见明显进展");
        }
        sb.append("。").append(frequencyText(ctx)).append("，如出现视力骤降或眼痛等情况请及时就医。");
        return sb.toString();
    }

    private String frequencyText(FollowupContext ctx) {
        int months;
        if ("PROGRESSION".equals(ctx.getRiskFlag()) || "SEVERE".equals(ctx.getRiskFlag())
                || ctx.isPostCxl()) {
            months = 3;
        } else if ("SUSPECT".equals(ctx.getStageCode()) || "CLINICAL".equals(ctx.getStageCode())
                || "ADVANCED".equals(ctx.getStageCode())) {
            months = 6;
        } else {
            months = 12;
        }
        return "建议每 " + months + " 个月复查一次（具体以眼科医师安排为准）";
    }

    private List<String> followUpItems(FollowupContext ctx) {
        List<String> items = new ArrayList<>(List.of(
                "裸眼视力与最佳矫正视力、验光",
                "角膜地形图/断层扫描（KMax、后表面参数）",
                "角膜最薄点厚度",
                "眼压"));
        if (ctx.isPostCxl()) {
            items.add("角膜上皮愈合与角膜混浊情况");
        }
        if ("CLINICAL".equals(ctx.getStageCode()) || "ADVANCED".equals(ctx.getStageCode())) {
            items.add("接触镜配适与角膜健康（如已验配）");
        }
        items.add("根据医师评估决定是否复查角膜生物力学");
        return items;
    }

    private List<String> warningSigns() {
        return List.of(
                "视力突然下降或视物变形明显加重",
                "眼痛、畏光、流泪",
                "揉眼后不适加重",
                "佩戴接触镜后出现眼红、疼痛或视力波动");
    }

    private List<String> lifestyleAdvice(FollowupContext ctx) {
        List<String> list = new ArrayList<>(List.of(
                "避免揉眼，这是需要长期坚持的重点",
                "如合并过敏性结膜炎，按医嘱规范管理",
                "遵医嘱佩戴和护理接触镜，注意手卫生",
                "保持规律作息与良好用眼习惯"));
        if (ctx.getAge() != null && ctx.getAge() < 18) {
            list.add("青少年患者建议家长共同关注随访时间与用眼行为");
        }
        return list;
    }

    private List<String> redFlags(FollowupContext ctx) {
        List<String> flags = new ArrayList<>();
        boolean severe = "SEVERE".equals(ctx.getRiskFlag());
        if (severe || ctx.isPostCxl()) {
            flags.add("如突发视力下降并伴眼痛、畏光、流泪，请立即前往眼科急诊（警惕急性圆锥角膜水肿）");
        } else {
            flags.add("如视力在短期内明显下降或视物变形加重，请尽快就诊");
        }
        flags.add("如眼部症状持续或加重，请及时就医，不要自行长期用药");
        return flags;
    }

    private String fmt(BigDecimal v, int scale) {
        return v.setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    @SafeVarargs
    private static <T> List<T> listOf(T... items) {
        return Arrays.asList(items);
    }
}
