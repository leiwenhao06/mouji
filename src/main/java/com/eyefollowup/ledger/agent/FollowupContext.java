package com.eyefollowup.ledger.agent;

import com.eyefollowup.ledger.entity.FollowupExam;
import com.eyefollowup.ledger.entity.FollowupPlan;
import com.eyefollowup.ledger.entity.Patient;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 输入上下文：只包含脱敏临床信息，不含姓名、电话等身份字段。
 */
@Data
public class FollowupContext {

    private String patientId;
    private String gender;
    private Integer age;

    private FollowupExam latestExam;
    private FollowupExam latestRight;
    private FollowupExam latestLeft;
    private List<FollowupExam> recentExams = new ArrayList<>();
    private FollowupPlan plan;

    private String stageCode;
    private String riskFlag;
    private String stageLabel;
    private String riskLabel;
    private String detectionBasis;
    private String detectionConfidence;
    private boolean postCxl;
    private int examCount;

    /** 便于规则引擎复用：装载患者后立即清除姓名等直接标识。 */
    public static FollowupContext from(Patient patient) {
        FollowupContext ctx = new FollowupContext();
        ctx.setPatientId(patient.getPatientId());
        ctx.setGender(patient.getGender());
        ctx.setAge(patient.getAge());
        return ctx;
    }
}
