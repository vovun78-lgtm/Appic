package com.example.appic.database.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "field_templates")
public class FieldTemplate {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "type")
    private String type; // TEXT, NUMBER, DATE, DATETIME, CHECKBOX, DROPDOWN и т.д.

    @ColumnInfo(name = "options")
    private String options; // JSON массив строк для DROPDOWN

    @ColumnInfo(name = "is_required")
    private boolean isRequired;

    @ColumnInfo(name = "sort_order")
    private int sortOrder;

    @ColumnInfo(name = "is_auto_fill", defaultValue = "0")
    private boolean isAutoFill;

    public FieldTemplate(int id, String name, String type, String options, boolean isRequired, int sortOrder, boolean isAutoFill) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.options = options;
        this.isRequired = isRequired;
        this.sortOrder = sortOrder;
        this.isAutoFill = isAutoFill;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public boolean isRequired() { return isRequired; }
    public void setRequired(boolean required) { isRequired = required; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isAutoFill() { return isAutoFill; }
    public void setAutoFill(boolean autoFill) { isAutoFill = autoFill; }
}