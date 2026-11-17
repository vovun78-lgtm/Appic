package com.example.appic.database.repository;

import android.content.Context;

import com.example.appic.database.DatabaseClient;
import com.example.appic.database.dao.FieldDao;
import com.example.appic.database.dao.MediaDao;
import com.example.appic.database.models.Field;
import com.example.appic.database.models.Media;
import com.example.appic.utils.FileUtils;

import java.util.List;

public class FieldRepository {

    private final DatabaseClient dbClient;
    private final FieldDao fieldDao;
    private final MediaDao mediaDao;

    public FieldRepository(Context context) {
        this.dbClient = DatabaseClient.getInstance(context);
        this.fieldDao = dbClient.fieldDao();
        this.mediaDao = dbClient.mediaDao();
    }

    public long insert(Field field) {
        return fieldDao.insert(field);
    }

    /**
     * Возвращает следующий свободный sort_order для группы/ungrouped.
     * Должен вызываться внутри транзакции, если далее идёт вставка.
     */
    public int getNextOrder(int machineId, int groupId) {
        Integer maxOrder = (groupId == 0)
                ? fieldDao.getMaxOrderForUngrouped(machineId)
                : fieldDao.getMaxOrderForGroup(machineId, groupId);
        return maxOrder == null ? 0 : maxOrder + 1;
    }

    public long insertWithOrder(Field field) {
        return dbClient.database().runInTransaction(() -> {
            int order = getNextOrder(field.getMachineId(), field.getGroupId());
            field.setOrder(order);
            return fieldDao.insert(field);
        });
    }

    public void update(Field field) {
        fieldDao.update(field);
    }

    public void markAsRead(int fieldId) {
        fieldDao.markAsRead(fieldId);
    }

    public Field getById(int id) {
        return fieldDao.getById(id);
    }

    public List<Field> getByMachineId(int machineId) {
        return fieldDao.getByMachineId(machineId);
    }

    public List<Field> getByGroupId(int groupId) {
        return fieldDao.getByGroupId(groupId);
    }

    /**
     * Перемещает поле в другую группу, назначая ему последнюю позицию
     * в целевой группе и пересортировывая старую группу.
     */
    public void moveToGroup(int fieldId, int groupId) {
        dbClient.database().runInTransaction(() -> {
            Field existing = fieldDao.getById(fieldId);
            if (existing == null) return;
            int oldGroupId = existing.getGroupId();
            int machineId = existing.getMachineId();
            if (oldGroupId == groupId) return;

            int order = getNextOrder(machineId, groupId);

            fieldDao.moveToGroup(fieldId, groupId, order, System.currentTimeMillis());

            if (oldGroupId == 0) {
                fieldDao.reorderUngrouped(machineId);
            } else {
                fieldDao.reorderFieldsInGroup(oldGroupId);
            }
        });
    }

    public void updateOrder(List<Field> fieldsInOrder, int groupId) {
        dbClient.database().runInTransaction(() -> {
            int i = 0;
            for (Field f : fieldsInOrder) {
                if (f.getGroupId() == groupId) {
                    fieldDao.updateOrderSingle(f.getId(), i);
                    i++;
                }
            }
        });
    }

    public void delete(int fieldId) {
        Field field = fieldDao.getById(fieldId);
        if (field == null) return;
        final int machineId = field.getMachineId();
        final int groupId = field.getGroupId();

        List<String> paths = mediaDao.getPathsByFieldId(fieldId);

        dbClient.database().runInTransaction(() -> {
            mediaDao.deleteByFieldId(fieldId);
            fieldDao.deleteById(fieldId);
            if (groupId == 0) {
                fieldDao.reorderUngrouped(machineId);
            } else {
                fieldDao.reorderFieldsInGroup(groupId);
            }
        });

        for (String p : paths) FileUtils.deleteFile(p);
    }

    public long insertMedia(Media media) {
        return mediaDao.insert(media);
    }

    public List<Media> getMediaByFieldId(int fieldId) {
        return mediaDao.getByFieldId(fieldId);
    }

    public void deleteMedia(Media media) {
        if (media.getId() != 0) {
            mediaDao.deleteById(media.getId());
        }
        FileUtils.deleteFile(media.getPath());
    }
}