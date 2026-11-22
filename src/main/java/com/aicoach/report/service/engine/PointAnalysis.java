package com.aicoach.report.service.engine;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PointAnalysis {

    private List<String> covered;

    private List<String> missing;

    private int coverageRate;

    private boolean sufficientEvidence;
}