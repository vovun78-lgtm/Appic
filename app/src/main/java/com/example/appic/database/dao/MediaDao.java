package com.example.appic.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.appic.database.models.Media;

import java.util.List;

@Dao
public interface MediaDao {

    @Insert
    long insert(Media media);

    @Update
    void update(Media media);

    @Delete
    void delete(Media media);

    @Query("DELETE FROM media WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM media WHERE field_id = :fieldId")
    void deleteByFieldId(int fieldId);

    @Query("DELETE FROM media WHERE field_id IN (:fieldIds)")
    void deleteByFieldIds(List<Integer> fieldIds);

    @Query("DELETE FROM media WHERE field_id IN (SELECT id FROM fields WHERE machine_id = :machineId)")
    void deleteByMachineId(int machineId);

    @Query("SELECT * FROM media WHERE id = :id LIMIT 1")
    Media getById(int id);

    @Query("SELECT * FROM media WHERE field_id = :fieldId")
    List<Media> getByFieldId(int fieldId);

    @Query("SELECT path FROM media WHERE field_id = :fieldId")
    List<String> getPathsByFieldId(int fieldId);

    @Query("SELECT path FROM media WHERE field_id IN (SELECT id FROM fields WHERE machine_id = :machineId)")
    List<String> getPathsByMachineId(int machineId);
}