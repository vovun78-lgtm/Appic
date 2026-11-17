package com.example.appic.utils.statistics;

import java.util.ArrayList;
import java.util.List;

public class FilterCondition {

    /** Ключ поля: "createdAt", "updatedAt", "isRead", "hasMedia", "template:42" */
    public String fieldKey;

    public FilterOp op;

    public String stringValue;
    public Double numberValue;
    public Long dateStart;
    public Long dateEnd;
    public Boolean boolValue;
    public List<String> listValues = new ArrayList<>();

    public FilterCondition() {}

    public FilterCondition(String fieldKey, FilterOp op) {
        this.fieldKey = fieldKey;
        this.op = op;
    }
}