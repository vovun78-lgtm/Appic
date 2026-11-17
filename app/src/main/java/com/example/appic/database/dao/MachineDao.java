package com.example.appic.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.appic.database.models.Machine;
import com.example.appic.database.models.MachineWithUnread;

import java.util.List;

@Dao
public interface MachineDao {

    @Insert
    long insert(Machine machine);

    @Update
    void update(Machine machine);

    @Query("DELETE FROM machines WHERE id = :id")
    void deleteById(int id);

    @Query("SELECT * FROM machines WHERE id = :id LIMIT 1")
    Machine getById(int id);

    @Query("SELECT m.*, " +
            "EXISTS(SELECT 1 FROM fields f WHERE f.machine_id = m.id AND f.is_read = 0) AS has_unread " +
            "FROM machines m ORDER BY m.sort_order ASC")
    List<MachineWithUnread> getAllWithUnread();

    @Query("SELECT m.*, " +
            "EXISTS(SELECT 1 FROM fields f WHERE f.machine_id = m.id AND f.is_read = 0) AS has_unread " +
            "FROM machines m WHERE m.group_id = 0 ORDER BY m.sort_order ASC")
    List<MachineWithUnread> getUngroupedWithUnread();

    @Query("SELECT m.*, " +
            "EXISTS(SELECT 1 FROM fields f WHERE f.machine_id = m.id AND f.is_read = 0) AS has_unread " +
            "FROM machines m WHERE m.group_id = :groupId ORDER BY m.sort_order ASC")
    List<MachineWithUnread> getByGroupIdWithUnread(int groupId);

    @Query("SELECT * FROM machines WHERE group_id = 0 ORDER BY sort_order ASC")
    List<Machine> getUngrouped();

    @Query("SELECT * FROM machines WHERE group_id = :groupId ORDER BY sort_order ASC")
    List<Machine> getByGroupId(int groupId);

    @Query("UPDATE machines SET sort_order = :order WHERE id = :id")
    void updateOrderSingle(int id, int order);

    @Query("UPDATE machines SET group_id = :groupId, sort_order = :order WHERE id = :machineId")
    void moveToGroup(int machineId, int groupId, int order);

    @Query("UPDATE machines SET group_id = :groupId WHERE id = :machineId")
    void updateGroupId(int machineId, int groupId);

    @Query("SELECT MAX(sort_order) FROM machines")
    Integer getMaxOrder();

    @Query("SELECT MAX(sort_order) FROM machines WHERE group_id = :groupId")
    Integer getMaxOrderForGroup(int groupId);

    @Query("SELECT id FROM machines ORDER BY sort_order ASC")
    List<Integer> getMachineIdsOrdered();

    @Query("SELECT id FROM machines WHERE group_id = :groupId ORDER BY sort_order ASC")
    List<Integer> getMachineIdsOrderedByGroup(int groupId);

    @Transaction
    default void reorderAll() {
        List<Integer> ids = getMachineIdsOrdered();
        for (int i = 0; i < ids.size(); i++) {
            updateOrderSingle(ids.get(i), i);
        }
    }

    @Transaction
    default void reorderGroup(int groupId) {
        List<Integer> ids = getMachineIdsOrderedByGroup(groupId);
        for (int i = 0; i < ids.size(); i++) {
            updateOrderSingle(ids.get(i), i);
        }
    }
}