package com.example.appic.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.appic.database.models.ReportDefinition;

import java.util.List;

@Dao
public interface ReportDefinitionDao {

    @Insert
    long insert(ReportDefinition def);

    @Update
    void update(ReportDefinition def);

    @Query("DELETE FROM report_definitions WHERE id = :id")
    void deleteById(int id);

    @Query("SELECT * FROM report_definitions ORDER BY updated_at DESC")
    List<ReportDefinition> getAll();

    @Query("SELECT * FROM report_definitions WHERE id = :id LIMIT 1")
    ReportDefinition getById(int id);

    @Query("SELECT COUNT(*) FROM report_definitions")
    int getCount();
}