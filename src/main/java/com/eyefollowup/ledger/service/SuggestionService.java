package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.agent.ContextAssembler;
import com.eyefollowup.ledger.agent.FollowupContext;
import com.eyefollowup.ledger.agent.KnowledgeRetriever;
import com.eyefollowup.ledger.agent.LlmClient;
import com.eyefollowup.ledger.agent.PromptEngine;
import com.eyefollowup.ledger.agent.RuleBasedFollowupRecommender;
import com.eyefollowup.ledger.common.BizException;
import com.eyefollowup.ledger.dao.KnowledgeDAO;
import com.eyefollowup.ledger.dao.SuggestionDAO;
import com.eyefollowup.ledger.dto.FollowupAdvice;
import com.eyefollowup.ledger.entity.FollowupSuggestion;
import com.eyefollowup.ledger.entity.LocalKnowledge;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 随访建议编排：
 * 上下文组装 → 本地知识检索 → 循证规则草稿 →（可选）大模型个体化 → 指南一致性守卫 → 结构化输出。
 * 大模型不可用/超时/输出不可解析时，直接使用规则草稿（同一输出契约）。
 */
@Slf4j
@Service
public class SuggestionService {

    private final SuggestionDAO suggestionDAO;
    private final ContextAssembler contextAssembler;
    private final KnowledgeRetriever knowledgeRetriever;
    private final RuleBasedFollowupRecommender recommender;
    private final PromptEngine promptEngine;
    private final LlmClient llmClient;
    private final ObjectMapper mapper;
    private final AuditService auditService;
    private final KnowledgeDAO knowledgeDAO;
    private final ThreadPoolExecutor agentExecutor;

    public SuggestionService(SuggestionDAO suggestionDAO,
                             ContextAssembler contextAssembler,
                             KnowledgeRetriever knowledgeRetriever,
                             RuleBasedFollowupRecommender recommender,
                             PromptEngine promptEngine,
                             LlmClient llmClient,
                             ObjectMapper mapper,
                             AuditService auditService,
                             KnowledgeDAO knowledgeDAO,
                             @Qualifier("agentExecutor") ThreadPoolExecutor agentExecutor) {
        this.suggestionDAO = suggestionDAO;
        this.contextAssembler = contextAssembler;
        this.knowledgeRetriever = knowledgeRetriever;
        this.recommender = recommender;
        this.promptEngine = promptEngine;
        this.llmClient = llmClient;
        this.mapper = mapper;
        this.auditService = auditService;
        this.knowledgeDAO = knowledgeDAO;
        this.agentExecutor = agentExecutor;
    }

    /**
     * 提交异步生成任务。同步完成上下文校验，失败可立即反馈。
     */
    public FollowupSuggestion submit(String patientId, String username, String ip) {
        FollowupContext ctx = contextAssembler.assemble(patientId);

        String requestId = UUID.randomUUID().toString().replace("-", "");
        FollowupSuggestion row = new FollowupSuggestion();
        row.setRequestId(requestId);
        row.setPatientId(patientId);
        row.setInputMode("PATIENT");
        row.setStatus("PENDING");
        row.setRequestedBy(username);
        suggestionDAO.insertPending(row);

        try {
            agentExecutor.execute(() -> process(requestId, ctx, username, ip));
        } catch (RejectedExecutionException e) {
            suggestionDAO.markFailed(requestId, "系统繁忙，请稍后重试");
            throw new BizException(429, "AI 建议任务繁忙，请稍后重试");
        }
        return suggestionDAO.findByRequestId(requestId);
    }

    private void process(String requestId, FollowupContext ctx, String username, String ip) {
        long start = System.currentTimeMillis();
        try {
            FollowupSuggestion row = suggestionDAO.findByRequestId(requestId);
            long waitMs = 0;
            if (row != null && row.getRequestedAt() != null) {
                waitMs = Duration.between(row.getRequestedAt(), LocalDateTime.now()).toMillis();
            }
            suggestionDAO.markRunning(requestId);

            List<LocalKnowledge> knowledge = knowledgeRetriever.retrieve(ctx, 5);
            FollowupAdvice draft = recommender.build(ctx, knowledge);

            String engine = "rules";
            FollowupAdvice finalAdvice = draft;

            if (llmClient.isEnabled() && llmClient.healthy()) {
                try {
                    String content = llmClient.chat(
                            promptEngine.systemPrompt(),
                            promptEngine.userPrompt(ctx, draft, knowledge));
                    FollowupAdvice llmAdvice = mapper.readValue(cleanJson(content), FollowupAdvice.class);
                    finalAdvice = mergeWithGuard(draft, llmAdvice);
                    engine = "llm";
                } catch (Exception e) {
                    log.warn("大模型生成失败，降级为规则草稿: {}", e.getMessage());
                }
            }

            String json = mapper.writeValueAsString(finalAdvice);
            suggestionDAO.markSuccess(requestId, engine, finalAdvice.getSummary(), json,
                    finalAdvice.getDisclaimer(), waitMs, System.currentTimeMillis() - start);
            auditService.logAs(username, "SUGGESTION", "GENERATE",
                    "生成随访建议 " + requestId + "（engine=" + engine + "，"
                            + (System.currentTimeMillis() - start) + "ms）", ctx.getPatientId(), ip);
        } catch (Exception e) {
            log.error("生成随访建议失败: {}", requestId, e);
            suggestionDAO.markFailed(requestId, truncate(e.getMessage(), 500));
            auditService.logAs(username, "SUGGESTION", "FAILED",
                    "生成失败 " + requestId + "：" + e.getMessage(), ctx.getPatientId(), ip);
        }
    }

    /**
     * 指南一致性守卫：大模型只允许补充个体化内容，不得删减规则草稿中的随访骨架。
     */
    private FollowupAdvice mergeWithGuard(FollowupAdvice draft, FollowupAdvice llm) {
        if (llm == null) {
            return draft;
        }
        if (isBlank(llm.getSummary())) {
            llm.setSummary(draft.getSummary());
        }

        FollowupAdvice.StageAssessment ls = llm.getStageAssessment();
        FollowupAdvice.StageAssessment ds = draft.getStageAssessment();
        if (ls == null || isBlank(ls.getStage())) {
            llm.setStageAssessment(ds);
        } else {
            if (ls.getBasis() == null || ls.getBasis().isEmpty()) ls.setBasis(ds.getBasis());
            if (isBlank(ls.getConfidence())) ls.setConfidence(ds.getConfidence());
        }

        FollowupAdvice.FollowUpPlan lp = llm.getFollowUpPlan();
        FollowupAdvice.FollowUpPlan dp = draft.getFollowUpPlan();
        if (lp == null || isBlank(lp.getFrequency())) {
            llm.setFollowUpPlan(dp);
        } else {
            if (lp.getItems() == null || lp.getItems().isEmpty()) lp.setItems(dp.getItems());
            if (lp.getWarningSigns() == null || lp.getWarningSigns().isEmpty()) {
                lp.setWarningSigns(dp.getWarningSigns());
            }
        }

        if (llm.getLifestyleAdvice() == null || llm.getLifestyleAdvice().isEmpty()) {
            llm.setLifestyleAdvice(draft.getLifestyleAdvice());
        }

        // 危险信号取并集，规则草稿的急症提示始终排在最前
        List<String> merged = new java.util.ArrayList<>(draft.getRedFlags());
        if (llm.getRedFlags() != null) {
            for (String f : llm.getRedFlags()) {
                if (f != null && !f.isBlank() && !merged.contains(f)) {
                    merged.add(f);
                }
            }
        }
        llm.setRedFlags(merged);

        if (llm.getKnowledgeRefs() == null || llm.getKnowledgeRefs().isEmpty()) {
            llm.setKnowledgeRefs(draft.getKnowledgeRefs());
        }
        // 免责声明始终使用固定文本
        llm.setDisclaimer(draft.getDisclaimer());
        return llm;
    }

    private String cleanJson(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            int first = s.indexOf('{');
            int last = s.lastIndexOf('}');
            if (first >= 0 && last > first) {
                s = s.substring(first, last + 1);
            }
        }
        return s;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String truncate(String s, int max) {
        if (s == null) return "生成失败";
        return s.length() > max ? s.substring(0, max) : s;
    }

    public FollowupSuggestion get(String requestId) {
        FollowupSuggestion s = suggestionDAO.findByRequestId(requestId);
        if (s == null) {
            throw new BizException(404, "建议任务不存在");
        }
        return s;
    }

    public List<FollowupSuggestion> listByPatient(String patientId, int limit) {
        return suggestionDAO.listByPatient(patientId, Math.min(Math.max(limit, 1), 100));
    }

    public List<FollowupSuggestion> recent(int limit) {
        return suggestionDAO.listRecent(Math.min(Math.max(limit, 1), 100));
    }

    public void adopt(int suggestionId, boolean adopted, String username) {
        FollowupSuggestion s = suggestionDAO.findById(suggestionId);
        if (s == null) {
            throw new BizException(404, "建议不存在");
        }
        suggestionDAO.updateAdoption(suggestionId, adopted, username);
        auditService.log("SUGGESTION", adopted ? "ADOPT" : "REJECT",
                "随访建议 #" + suggestionId + (adopted ? " 采纳" : " 不采纳"), s.getPatientId());
    }

    public Map<String, Object> status() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("llmEnabled", llmClient.isEnabled());
        map.put("llmHealthy", llmClient.healthy());
        map.put("activeThreads", agentExecutor.getActiveCount());
        map.put("queueRemaining", agentExecutor.getQueue().remainingCapacity());
        map.put("knowledgeCount", knowledgeDAO.findAllEnabled().size());
        map.put("engineDefault", llmClient.isEnabled() && llmClient.healthy() ? "llm+rules" : "rules");
        return map;
    }
}
