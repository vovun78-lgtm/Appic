package com.example.appic.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.appic.database.models.Group;
import com.example.appic.database.models.GroupWithUnread;

import java.util.List;

@Dao
public interface GroupDao {

    @Insert
    long insert(Group group);

    @Update
    void update(Group group);

    @Query("DELETE FROM groups WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM groups WHERE machine_id = :machineId")
    void deleteByMachineId(int machineId);

    @Query("SELECT * FROM groups WHERE id = :id LIMIT 1")
    Group getById(int id);

    @Query("SELECT g.*, " +
            "EXISTS(SELECT 1 FROM fields f WHERE f.group_id = g.id AND f.is_read = 0) AS has_unread " +
            "FROM groups g WHERE g.machine_id = :machineId ORDER BY g.sort_order ASC")
    List<GroupWithUnread> getByMachineIdWithUnread(int machineId);

    @Query("SELECT EXISTS(SELECT 1 FROM fields f WHERE f.group_id = :groupId AND f.is_read = 0)")
    boolean hasUnreadFields(int groupId);

    @Query("UPDATE groups SET sort_order = :order WHERE id = :id")
    void updateOrderSingle(int id, int order);

    @Query("SELECT MAX(sort_order) FROM groups WHERE machine_id = :machineId")
    Integer getMaxOrderForMachine(int machineId);

    @Query("SELECT id FROM groups WHERE machine_id = :machineId ORDER BY sort_order ASC")
    List<Integer> getGroupIdsOrdered(int machineId);

    @Transaction
    default void reorderGroupsForMachine(int machineId) {
        List<Integer> ids = getGroupIdsOrdered(machineId);
        for (int i = 0; i < ids.size(); i++) {
            updateOrderSingle(ids.get(i), i);
        }
    }
}