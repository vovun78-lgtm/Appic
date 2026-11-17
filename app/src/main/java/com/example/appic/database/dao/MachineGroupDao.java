package com.example.appic.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.appic.database.models.MachineGroup;

import java.util.List;

@Dao
public interface MachineGroupDao {

    @Insert
    long insert(MachineGroup group);

    @Update
    void update(MachineGroup group);

    @Query("DELETE FROM machine_groups WHERE id = :id")
    void deleteById(int id);

    @Query("SELECT * FROM machine_groups WHERE id = :id LIMIT 1")
    MachineGroup getById(int id);

    @Query("SELECT * FROM machine_groups ORDER BY sort_order ASC")
    List<MachineGroup> getAll();

    @Query("UPDATE machine_groups SET sort_order = :order WHERE id = :id")
    void updateOrderSingle(int id, int order);

    @Query("SELECT MAX(sort_order) FROM machine_groups")
    Integer getMaxOrder();

    @Query("SELECT id FROM machine_groups ORDER BY sort_order ASC")
    List<Integer> getGroupIdsOrdered();

    @Query("SELECT id FROM machine_groups WHERE name = :name LIMIT 1")
    Integer findIdByName(String name);

    @Transaction
    default void reorderAll() {
        List<Integer> ids = getGroupIdsOrdered();
        for (int i = 0; i < ids.size(); i++) {
            updateOrderSingle(ids.get(i), i);
        }
    }
}