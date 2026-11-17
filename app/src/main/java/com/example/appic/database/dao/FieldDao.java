package com.example.appic.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.appic.database.models.Field;

import java.util.List;

@Dao
public interface FieldDao {

    @Insert
    long insert(Field field);

    @Update
    void update(Field field);

    @Query("UPDATE fields SET is_read = 1 WHERE id = :fieldId")
    void markAsRead(int fieldId);

    @Query("DELETE FROM fields WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM fields WHERE machine_id = :machineId")
    void deleteByMachineId(int machineId);

    @Query("SELECT id FROM fields WHERE machine_id = :machineId")
    List<Integer> getFieldIdsByMachineId(int machineId);

    @Query("SELECT * FROM fields WHERE id = :id LIMIT 1")
    Field getById(int id);

    @Query("SELECT * FROM fields " +
            "WHERE machine_id = :machineId " +
            "AND (group_id = 0 OR group_id IS NULL) " +
            "ORDER BY sort_order ASC")
    List<Field> getByMachineId(int machineId);

    @Query("SELECT * FROM fields WHERE group_id = :groupId ORDER BY sort_order ASC")
    List<Field> getByGroupId(int groupId);

    @Query("UPDATE fields SET group_id = :groupId, sort_order = :order, updated_at = :updatedAt WHERE id = :fieldId")
    void moveToGroup(int fieldId, int groupId, int order, long updatedAt);

    @Query("UPDATE fields SET sort_order = :order WHERE id = :id")
    void updateOrderSingle(int id, int order);

    @Query("SELECT MAX(sort_order) FROM fields " +
            "WHERE machine_id = :machineId " +
            "AND (group_id = 0 OR group_id IS NULL)")
    Integer getMaxOrderForUngrouped(int machineId);

    @Query("SELECT MAX(sort_order) FROM fields " +
            "WHERE machine_id = :machineId AND group_id = :groupId")
    Integer getMaxOrderForGroup(int machineId, int groupId);

    @Query("SELECT id FROM fields " +
            "WHERE machine_id = :machineId " +
            "AND (group_id = 0 OR group_id IS NULL) " +
            "ORDER BY sort_order ASC")
    List<Integer> getUngroupedFieldIdsOrdered(int machineId);

    @Query("SELECT id FROM fields WHERE group_id = :groupId ORDER BY sort_order ASC")
    List<Integer> getGroupFieldIdsOrdered(int groupId);

    @Query("UPDATE fields SET updated_at = :updatedAt WHERE id = :id")
    void setUpdatedAt(int id, long updatedAt);

    @Transaction
    default void reorderFieldsInGroup(int groupId) {
        List<Integer> ids = getGroupFieldIdsOrdered(groupId);
        for (int i = 0; i < ids.size(); i++) {
            updateOrderSingle(ids.get(i), i);
        }
    }

    @Transaction
    default void reorderUngrouped(int machineId) {
        List<Integer> ids = getUngroupedFieldIdsOrdered(machineId);
        for (int i = 0; i < ids.size(); i++) {
            updateOrderSingle(ids.get(i), i);
        }
    }
}