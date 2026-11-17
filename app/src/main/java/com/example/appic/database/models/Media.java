package com.example.appic.database.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "media",
        foreignKeys = @ForeignKey(
                entity = Field.class,
                parentColumns = "id",
                childColumns = "field_id",
                onDelete = ForeignKey.NO_ACTION,
                onUpdate = ForeignKey.NO_ACTION
        ),
        indices = {
                @Index(name = "idx_media_field", value = {"field_id"}),
                @Index(name = "idx_media_path", value = {"path"})
        }
)
public class Media {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;

    @ColumnInfo(name = "field_id")
    private int fieldId;

    @ColumnInfo(name = "type")
    private String type;

    @ColumnInfo(name = "path")
    private String path;

    public Media(int id, int fieldId, String type, String path) {
        this.id = id;
        this.fieldId = fieldId;
        this.type = type;
        this.path = path;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getFieldId() { return fieldId; }
    public void setFieldId(int fieldId) { this.fieldId = fieldId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}