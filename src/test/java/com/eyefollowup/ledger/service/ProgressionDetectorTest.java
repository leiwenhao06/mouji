package com.eyefollowup.ledger.service;

import com.eyefollowup.ledger.entity.BiomechanicalParams;
import com.eyefollowup.ledger.entity.CornealTopography;
import com.eyefollowup.ledger.entity.FollowupExam;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 进展检测引擎的核心规则测试（不依赖 Spring / 数据库）。
 */
class ProgressionDetectorTest {

    private final ProgressionDetector detector = new ProgressionDetector();

    private FollowupExam exam(String date, String kmax, String thinnest, String backRmin,
                              String ctbi, String notes) {
        FollowupExam e = new FollowupExam();
        e.setExamId((int) (Math.random() * 100000));
        e.setExamDate(LocalDate.parse(date));
        e.setEyeSide("right");
        e.setNotes(notes);
        CornealTopography t = new CornealTopography();
        t.setFrontKmax(new BigDecimal(kmax));
        t.setThinnestPachy(new BigDecimal(thinnest));
        t.setBackRmin(new BigDecimal(backRmin));
        e.setTopography(t);
        BiomechanicalParams b = new BiomechanicalParams();
        b.setCtbi(new BigDecimal(ctbi));
        e.setBiomechanics(b);
        return e;
    }

    @Test
    void normalStableEye() {
        FollowupExam current = exam("2026-10-01", "44.0", "500", "6.0", "0.30", null);
        ProgressionDetector.Result r = detector.detect(current, List.of());
        assertEquals("NORMAL", r.getStageCode());
        assertEquals("NONE", r.getRiskFlag());
    }

    @Test
    void suspectByBackSurface() {
        FollowupExam current = exam("2026-10-01", "45.0", "490", "4.8", "0.40", null);
        ProgressionDetector.Result r = detector.detect(current, List.of());
        assertEquals("SUSPECT", r.getStageCode());
        assertEquals("SUSPECT", r.getRiskFlag());
    }

    @Test
    void progressionByAnnualizedKmax() {
        FollowupExam baseline = exam("2025-10-01", "45.5", "500", "5.5", "0.50", null);
        FollowupExam current = exam("2026-10-01", "46.8", "485", "5.4", "0.60", null);
        ProgressionDetector.Result r = detector.detect(current, List.of(baseline));
        assertEquals("SUSPECT", r.getStageCode());
        assertEquals("PROGRESSION", r.getRiskFlag());
        assertEquals("high", r.getConfidence());
    }

    @Test
    void severeWhenThinnestBelow400() {
        FollowupExam current = exam("2026-10-01", "52.5", "380", "4.2", "0.85", null);
        ProgressionDetector.Result r = detector.detect(current, List.of());
        assertEquals("ADVANCED", r.getStageCode());
        assertEquals("SEVERE", r.getRiskFlag());
    }

    @Test
    void postCrossLinkingKeepsStage() {
        FollowupExam current = exam("2026-10-01", "50.0", "420", "4.5", "0.70", "角膜交联术后随访");
        ProgressionDetector.Result r = detector.detect(current, List.of());
        assertEquals("POST_CXL", r.getStageCode());
        assertEquals("SUSPECT", r.getRiskFlag());
    }

    @Test
    void missingTopographyIsUnknown() {
        FollowupExam current = new FollowupExam();
        current.setExamDate(LocalDate.parse("2026-10-01"));
        current.setEyeSide("right");
        ProgressionDetector.Result r = detector.detect(current, List.of());
        assertEquals("UNKNOWN", r.getStageCode());
        assertEquals("NONE", r.getRiskFlag());
    }
}
