package com.eyefollowup.ledger.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 角膜地形图参数（Pentacam）。
 */
@Data
public class CornealTopography {
    private Integer id;
    private Integer examId;

    private BigDecimal frontK1;
    private BigDecimal frontK2;
    private BigDecimal frontKm;
    private BigDecimal frontKmax;
    private BigDecimal frontRmin;
    private BigDecimal frontQVal;
    private BigDecimal frontAxis;
    private BigDecimal frontAstig;
    private String frontQs;

    private BigDecimal backK1;
    private BigDecimal backK2;
    private BigDecimal backKm;
    private BigDecimal backRmin;
    private BigDecimal backQVal;
    private String backQs;

    private BigDecimal pupilCenterPachy;
    private BigDecimal pachyApex;
    private BigDecimal thinnestPachy;

    private BigDecimal corneaVolume;
    private BigDecimal acDepth;
    private BigDecimal iop;
}
