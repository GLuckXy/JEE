package com.aiinterview.report.service.engine;

import lombok.Data;

@Data
public class ReportSummary {

    private Integer totalScore;

    private String dimensionScores;

    private String summary;

    private String weaknessTags;

    private String suggestions;
}
