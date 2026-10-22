package com.eyefollowup.ledger.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 随访建议的结构化输出契约（规则引擎与大模型共用）。
 * 合规红线：仅作随访管理参考，不包含药物剂量、疗程或手术决策。
 */
@Data
public class FollowupAdvice {

    private String summary;

    private StageAssessment stageAssessment = new StageAssessment();

    private FollowUpPlan followUpPlan = new FollowUpPlan();

    private List<String> lifestyleAdvice = new ArrayList<>();

    private List<String> redFlags = new ArrayList<>();

    private List<KnowledgeRef> knowledgeRefs = new ArrayList<>();

    private String disclaimer;

    @Data
    public static class StageAssessment {
        private String stage;
        private List<String> basis = new ArrayList<>();
        private String confidence;
    }

    @Data
    public static class FollowUpPlan {
        private String frequency;
        private List<String> items = new ArrayList<>();
        private List<String> warningSigns = new ArrayList<>();
    }

    @Data
    public static class KnowledgeRef {
        private String title;
        private String source;
        private String year;
    }
}
