package com.example.appic.utils.statistics;

import java.util.ArrayList;
import java.util.List;

public class StatisticsResult {

    public List<String> metricLabels = new ArrayList<>();
    public List<String> columnLabels = new ArrayList<>();
    public List<StatisticsRow> rows = new ArrayList<>();
    public String groupByLabel = "";
    public String errorMessage;

    public boolean isEmpty() {
        return rows == null || rows.isEmpty();
    }
}