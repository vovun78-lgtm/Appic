package com.example.appic.utils.statistics;

public enum GroupByType {
    NONE,        // одна строка (всё вместе)
    MACHINE,     // по объектам
    GROUP,       // по группам записей
    MONTH,       // по месяцам
    YEAR,        // по годам
    FIELD        // по значению кастомного поля (templateId)
}