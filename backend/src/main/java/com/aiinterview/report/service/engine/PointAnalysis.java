package com.aiinterview.report.service.engine;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PointAnalysis {

    private List<String> covered;

    private List<String> missing;

    private int coverageRate;

    private boolean sufficientEvidence;
}
