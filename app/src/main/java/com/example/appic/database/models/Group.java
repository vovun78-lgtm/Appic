package com.example.appic.database.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "groups",
        foreignKeys = @ForeignKey(
                entity = Machine.class,
                parentColumns = "id",
                childColumns = "machine_id",
                onDelete = ForeignKey.NO_ACTION,
                onUpdate = ForeignKey.NO_ACTION
        ),
        indices = {
                @Index(name = "idx_groups_machine", value = {"machine_id", "sort_order"})
        }
)
public class Group {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;

    @ColumnInfo(name = "machine_id")
    private int machineId;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    private int order;

    @Ignore
    private boolean hasUnread = false;

    public Group(int id, int machineId, String name, int order) {
        this.id = id;
        this.machineId = machineId;
        this.name = name;
        this.order = order;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getMachineId() { return machineId; }
    public void setMachineId(int machineId) { this.machineId = machineId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public boolean hasUnread() { return hasUnread; }
    public void setHasUnread(boolean hasUnread) { this.hasUnread = hasUnread; }
}