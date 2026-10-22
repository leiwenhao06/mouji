package com.eyefollowup.ledger.entity;

import lombok.Data;

/**
 * 本地医学知识库条目（公开指南/共识摘要）。
 */
@Data
public class LocalKnowledge {
    private Integer knowledgeId;
    private String category;
    private String title;
    private String content;
    private String source;
    private String pubYear;
    private String evidenceLevel;
    private String keywords;
    private Boolean enabled;
}
