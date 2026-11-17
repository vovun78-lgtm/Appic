package com.example.appic.database.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "fields",
        foreignKeys = @ForeignKey(
                entity = Machine.class,
                parentColumns = "id",
                childColumns = "machine_id",
                onDelete = ForeignKey.NO_ACTION,
                onUpdate = ForeignKey.NO_ACTION
        ),
        indices = {
                @Index(name = "idx_fields_machine", value = {"machine_id", "sort_order"}),
                @Index(name = "idx_fields_group", value = {"group_id", "sort_order"}),
                @Index(name = "idx_fields_machine_title", value = {"machine_id", "title"}),
                @Index(name = "idx_fields_machine_unread", value = {"machine_id", "is_read"}),
                @Index(name = "idx_fields_group_unread", value = {"group_id", "is_read"})
        }
)
public class Field {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;

    @ColumnInfo(name = "machine_id")
    private int machineId;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "text")
    private String text;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    private long updatedAt;

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    private int order;

    @ColumnInfo(name = "group_id", defaultValue = "0")
    private int groupId;

    @ColumnInfo(name = "is_read", defaultValue = "0")
    private boolean isRead;

    // НОВОЕ ПОЛЕ: хранит JSON-строку с ответами пользователя
    @ColumnInfo(name = "custom_values")
    private String customValues;

    public Field(int id, int machineId, String title, String text,
                 long createdAt, long updatedAt, int order, int groupId, boolean isRead, String customValues) {
        this.id = id;
        this.machineId = machineId;
        this.title = title;
        this.text = text;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.order = order;
        this.groupId = groupId;
        this.isRead = isRead;
        this.customValues = customValues;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getMachineId() { return machineId; }
    public void setMachineId(int machineId) { this.machineId = machineId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public String getCustomValues() { return customValues; }
    public void setCustomValues(String customValues) { this.customValues = customValues; }
}