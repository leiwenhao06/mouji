package com.eyefollowup.ledger.dao;

import com.eyefollowup.ledger.entity.LocalKnowledge;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 本地知识库数据访问（条目少，直接全量加载后在内存中打分检索）。
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeDAO {

    private final JdbcTemplate jdbc;

    private static final RowMapper<LocalKnowledge> MAPPER = (rs, i) -> {
        LocalKnowledge k = new LocalKnowledge();
        k.setKnowledgeId(rs.getInt("knowledge_id"));
        k.setCategory(rs.getString("category"));
        k.setTitle(rs.getString("title"));
        k.setContent(rs.getString("content"));
        k.setSource(rs.getString("source"));
        k.setPubYear(rs.getString("pub_year"));
        k.setEvidenceLevel(rs.getString("evidence_level"));
        k.setKeywords(rs.getString("keywords"));
        k.setEnabled(rs.getBoolean("enabled"));
        return k;
    };

    public List<LocalKnowledge> findAllEnabled() {
        return jdbc.query("SELECT * FROM local_knowledge WHERE enabled=1 ORDER BY knowledge_id", MAPPER);
    }
}
