package com.example.appic.utils.statistics;

import java.util.ArrayList;
import java.util.List;

public class StatisticsRow {
    public String label;
    public List<Double> metrics = new ArrayList<>();

    public StatisticsRow() {}

    public StatisticsRow(String label, List<Double> metrics) {
        this.label = label;
        this.metrics = metrics;
    }
}