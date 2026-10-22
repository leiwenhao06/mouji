package com.eyefollowup.ledger.agent;

import com.eyefollowup.ledger.dao.KnowledgeDAO;
import com.eyefollowup.ledger.entity.LocalKnowledge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地知识库检索：关键词打分，取最相关的条目注入提示词/规则引擎。
 * 不联网，患者身份信息不出本机。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeRetriever {

    private final KnowledgeDAO knowledgeDAO;

    public List<LocalKnowledge> retrieve(FollowupContext ctx, int limit) {
        List<LocalKnowledge> all = knowledgeDAO.findAllEnabled();
        if (all.isEmpty()) {
            return List.of();
        }
        Set<String> terms = buildTerms(ctx);

        List<Scored> scored = new ArrayList<>();
        for (LocalKnowledge k : all) {
            int score = 0;
            String keywords = k.getKeywords() == null ? "" : k.getKeywords();
            String title = k.getTitle() == null ? "" : k.getTitle();
            String content = k.getContent() == null ? "" : k.getContent();
            for (String term : terms) {
                if (keywords.contains(term)) score += 3;
                if (title.contains(term)) score += 2;
                if (content.contains(term)) score += 1;
            }
            scored.add(new Scored(k, score));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());

        // 若无明显命中，返回通用随访条目，保证有参考依据
        List<LocalKnowledge> result = new ArrayList<>();
        for (Scored s : scored) {
            if (result.size() >= limit) break;
            if (s.score > 0 || result.isEmpty()) {
                result.add(s.knowledge);
            }
        }
        return result;
    }

    private Set<String> buildTerms(FollowupContext ctx) {
        Set<String> terms = new LinkedHashSet<>();
        terms.add("随访");
        terms.add("复查");
        String stage = ctx.getStageCode() == null ? "" : ctx.getStageCode();
        String risk = ctx.getRiskFlag() == null ? "" : ctx.getRiskFlag();

        if ("SEVERE".equals(risk)) {
            terms.add("急性水肿");
            terms.add("厚度");
            terms.add("进展");
        }
        if ("PROGRESSION".equals(risk)) {
            terms.add("进展");
            terms.add("KMax");
            terms.add("厚度");
        }
        if ("POST_CXL".equals(stage) || ctx.isPostCxl()) {
            terms.add("交联");
            terms.add("术后");
        }
        if ("CLINICAL".equals(stage) || "ADVANCED".equals(stage)) {
            terms.add("接触镜");
            terms.add("巩膜镜");
            terms.add("交联");
        }
        if ("SUSPECT".equals(stage)) {
            terms.add("地形图");
            terms.add("可疑");
        }
        if (ctx.getAge() != null && ctx.getAge() < 18) {
            terms.add("儿童");
            terms.add("青少年");
            terms.add("揉眼");
        }
        terms.add("共识");
        return terms;
    }

    private record Scored(LocalKnowledge knowledge, int score) {
    }
}
