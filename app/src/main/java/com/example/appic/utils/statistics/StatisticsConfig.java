package com.example.appic.utils.statistics;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class StatisticsConfig {

    public int version = 1;

    public String name;

    public ScopeType scopeType = ScopeType.ALL;
    public List<Integer> scopeIds = new ArrayList<>();

    public Long startDate;
    public Long endDate;

    public List<FilterCondition> filters = new ArrayList<>();
    public LogicOp logic = LogicOp.AND;

    public GroupByType groupBy = GroupByType.MACHINE;
    public Integer groupByTemplateId;

    public List<MetricDef> metrics = new ArrayList<>();

    public ViewType viewType = ViewType.TABLE;

    public boolean sortDesc = true;

    public int limit = 100;

    public StatisticsConfig() {}

    public StatisticsConfig copy() {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(this), StatisticsConfig.class);
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static StatisticsConfig fromJson(String json) {
        if (json == null || json.isEmpty()) return new StatisticsConfig();
        try {
            StatisticsConfig c = new Gson().fromJson(json, StatisticsConfig.class);
            if (c == null) return new StatisticsConfig();
            return c;
        } catch (Exception e) {
            return new StatisticsConfig();
        }
    }
}