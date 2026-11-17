package com.example.appic.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.appic.database.models.FieldTemplate;

import java.util.List;

@Dao
public interface FieldTemplateDao {
    @Insert
    long insert(FieldTemplate template);

    @Update
    void update(FieldTemplate template);

    @Delete
    void delete(FieldTemplate template);

    @Query("SELECT * FROM field_templates ORDER BY sort_order ASC")
    List<FieldTemplate> getAll();

    @Query("SELECT COUNT(*) FROM field_templates")
    int getCount();

    @Query("SELECT MAX(sort_order) FROM field_templates")
    Integer getMaxOrder();
}