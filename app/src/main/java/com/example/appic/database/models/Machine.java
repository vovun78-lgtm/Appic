package com.example.appic.database.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "machines",
        indices = {
                @Index(name = "idx_machines_group", value = {"group_id", "sort_order"})
        }
)
public class Machine {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    private int order;

    @ColumnInfo(name = "group_id", defaultValue = "0")
    private int groupId;

    @Ignore
    private boolean hasUnread = false;

    public Machine(int id, String name, int order, int groupId) {
        this.id = id;
        this.name = name;
        this.order = order;
        this.groupId = groupId;
    }

    @Ignore
    public Machine(int id, String name, int order) {
        this(id, name, order, 0);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public boolean hasUnread() { return hasUnread; }
    public void setHasUnread(boolean hasUnread) { this.hasUnread = hasUnread; }
}