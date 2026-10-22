package com.eyefollowup.ledger.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 角膜生物力学参数（Corvis ST）。
 */
@Data
public class BiomechanicalParams {
    private Integer id;
    private Integer examId;
    private BigDecimal ccbi;
    private BigDecimal ctbi;
    private BigDecimal isValue;
    private BigDecimal spA1;
    private BigDecimal integrRadius;
    private BigDecimal arth;
    private BigDecimal daRatio;
    private BigDecimal ssi;
}
