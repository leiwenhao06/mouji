package com.eyefollowup.ledger.agent;

import com.eyefollowup.ledger.dto.FollowupAdvice;
import com.eyefollowup.ledger.entity.CornealTopography;
import com.eyefollowup.ledger.entity.FollowupExam;
import com.eyefollowup.ledger.entity.LocalKnowledge;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 提示词工程：系统提示（角色/红线/输出契约）+ 用户上下文（脱敏）。
 * 规则草稿作为"必须遵循的骨架"下发，模型只做个体化补充。
 */
@Component
@RequiredArgsConstructor
public class PromptEngine {

    private final ObjectMapper mapper;

    public String systemPrompt() {
        return """
                你是眼科随访管理助手，服务于圆锥角膜患者的长期随访台账。你的任务是在已有循证草稿的基础上，
                生成一份面向医生核对的结构化随访管理建议。

                必须遵守的规则：
                1. 只做随访管理建议，不做诊断结论，不推荐具体药物、剂量、疗程，不做手术决策；
                2. 医生确认的信息优先于系统推断；不确定时如实说明，不得编造检查结果或文献；
                3. 急性圆锥角膜水肿等急症风险必须放在 redFlags 第一条，提示立即就医；
                4. 输出必须是单个 JSON 对象，不要输出 Markdown、注释或额外文字；
                5. 必须保留规则草稿中的所有关键随访项目与警告信号，只能补充个体化内容；
                6. disclaimer 字段必须原样使用给定文本。

                输出 JSON 契约（字段名必须一致）：
                {
                  "summary": "一段话总结",
                  "stageAssessment": {"stage": "分期", "basis": ["依据1"], "confidence": "高/中/低"},
                  "followUpPlan": {"frequency": "随访频率", "items": ["复查项目"], "warningSigns": ["警告信号"]},
                  "lifestyleAdvice": ["生活管理建议"],
                  "redFlags": ["危险信号，急症在第一条"],
                  "knowledgeRefs": [{"title": "标题", "source": "来源", "year": "年份"}],
                  "disclaimer": "固定免责声明"
                }
                """;
    }

    public String userPrompt(FollowupContext ctx, FollowupAdvice draft, List<LocalKnowledge> knowledge) {
        StringBuilder sb = new StringBuilder();
        sb.append("【患者脱敏信息】\n");
        sb.append("性别：").append(nullSafe(ctx.getGender())).append("\n");
        sb.append("年龄：").append(ctx.getAge() == null ? "未知" : ctx.getAge() + " 岁").append("\n");
        sb.append("检查次数：").append(ctx.getExamCount()).append("\n\n");

        sb.append("【当前评估】\n");
        sb.append("分期：").append(nullSafe(ctx.getStageLabel())).append("\n");
        sb.append("风险标记：").append(nullSafe(ctx.getRiskLabel())).append("\n");
        sb.append("检测依据：").append(nullSafe(ctx.getDetectionBasis())).append("\n\n");

        sb.append("【最近检查指标】\n");
        appendExam(sb, ctx.getLatestRight(), "右眼");
        appendExam(sb, ctx.getLatestLeft(), "左眼");
        sb.append("\n");

        sb.append("【当前随访计划】\n");
        if (ctx.getPlan() == null) {
            sb.append("暂无有效计划\n");
        } else {
            sb.append("频率：每 ").append(ctx.getPlan().getFrequencyMonths()).append(" 个月；")
              .append("下次应随访：").append(ctx.getPlan().getNextDueDate()).append("；")
              .append("状态：").append(ctx.getPlan().getStatus()).append("\n");
        }
        sb.append("\n");

        if (knowledge != null && !knowledge.isEmpty()) {
            sb.append("【本地知识库参考（仅可使用这些来源，不得编造）】\n");
            int i = 1;
            for (LocalKnowledge k : knowledge) {
                sb.append(i++).append(". ").append(k.getTitle())
                  .append("（").append(nullSafe(k.getSource())).append("，").append(nullSafe(k.getPubYear())).append("）\n");
                String content = k.getContent() == null ? "" : k.getContent();
                sb.append(content.length() > 400 ? content.substring(0, 400) + "…" : content).append("\n");
            }
            sb.append("\n");
        }

        sb.append("【循证规则草稿（必须遵循的骨架，不得删减一线随访项目）】\n");
        try {
            sb.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(draft)).append("\n\n");
        } catch (Exception e) {
            sb.append(draft.toString()).append("\n\n");
        }

        sb.append("请基于以上信息输出个体化补充后的完整 JSON。注意：不得改变规则草稿中的随访频率档次、");
        sb.append("不得删减危机提示；如信息不足，保持谨慎并在 confidence 中体现。");
        return sb.toString();
    }

    private void appendExam(StringBuilder sb, FollowupExam exam, String label) {
        if (exam == null || exam.getTopography() == null) {
            sb.append(label).append("：无有效地形图数据\n");
            return;
        }
        CornealTopography t = exam.getTopography();
        sb.append(label).append("（").append(exam.getExamDate()).append("）：");
        if (t.getFrontKmax() != null) sb.append("KMax ").append(t.getFrontKmax()).append("D ");
        if (t.getThinnestPachy() != null) sb.append("最薄点 ").append(t.getThinnestPachy()).append("μm ");
        if (t.getFrontKm() != null) sb.append("Km ").append(t.getFrontKm()).append("D ");
        if (t.getBackRmin() != null) sb.append("后表面Rmin ").append(t.getBackRmin()).append("mm ");
        if (exam.getBiomechanics() != null && exam.getBiomechanics().getCtbi() != null) {
            sb.append("cTBI ").append(exam.getBiomechanics().getCtbi());
        }
        sb.append("\n");
    }

    private String nullSafe(String s) {
        return s == null ? "未知" : s;
    }
}
