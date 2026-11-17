package com.example.appic.utils.statistics;

public class MetricDef {

    public MetricType type = MetricType.COUNT;

    /** Для COUNT — null. Для остальных — "template:42" или "createdAt" */
    public String fieldKey;

    public MetricDef() {}

    public MetricDef(MetricType type) {
        this.type = type;
    }

    public MetricDef(MetricType type, String fieldKey) {
        this.type = type;
        this.fieldKey = fieldKey;
    }
}