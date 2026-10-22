package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.dao.DashboardDAO;
import com.eyefollowup.ledger.dao.PlanDAO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据看板聚合层：只读查询 + TTL 缓存。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardDAO dashboardDAO;
    private final PlanDAO planDAO;

    @Value("${app.dashboard.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    private volatile Map<String, Object> cache;
    private volatile long cacheAt = 0L;

    public Map<String, Object> overview(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cache != null && now - cacheAt < cacheTtlSeconds * 1000) {
            return withAge(cache, (now - cacheAt) / 1000);
        }
        synchronized (this) {
            if (!forceRefresh && cache != null && System.currentTimeMillis() - cacheAt < cacheTtlSeconds * 1000) {
                return withAge(cache, (System.currentTimeMillis() - cacheAt) / 1000);
            }
            Map<String, Object> data = build();
            cache = data;
            cacheAt = System.currentTimeMillis();
            return withAge(data, 0);
        }
    }

    private Map<String, Object> withAge(Map<String, Object> data, long ageSeconds) {
        Map<String, Object> copy = new LinkedHashMap<>(data);
        copy.put("cacheAgeSeconds", ageSeconds);
        return copy;
    }

    private Map<String, Object> build() {
        Map<String, Object> result = new LinkedHashMap<>();

        planDAO.markOverdue();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalPatients", dashboardDAO.totalPatients());
        summary.put("totalExams", dashboardDAO.totalExams());
        summary.put("examsThisMonth", dashboardDAO.examsThisMonth());
        summary.put("overduePlans", planDAO.countOverdue());
        summary.put("progressionPatients", dashboardDAO.progressionPatients());
        result.put("summary", summary);

        result.put("stageDistribution", dashboardDAO.stageDistribution());
        result.put("riskDistribution", dashboardDAO.riskDistribution());
        result.put("monthlyExams", dashboardDAO.monthlyExams());
        result.put("ageDistribution", dashboardDAO.ageDistribution());
        result.put("progressionTop", dashboardDAO.progressionTop());
        result.put("overduePlans", planDAO.listOverdue(10));

        Map<String, Object> suggest = dashboardDAO.suggestionStats();
        if (suggest != null) {
            if (suggest.get("adopted") == null) suggest.put("adopted", 0);
            if (suggest.get("total") == null) suggest.put("total", 0);
            if (suggest.get("avg_duration_ms") == null) suggest.put("avg_duration_ms", 0);
        }
        result.put("suggestionStats", suggest);
        return result;
    }
}
