package com.example.appic.database.repository;

import android.content.Context;

import com.example.appic.database.DatabaseClient;
import com.example.appic.database.dao.FieldDao;
import com.example.appic.database.dao.GroupDao;
import com.example.appic.database.models.Group;
import com.example.appic.database.models.GroupWithUnread;

import java.util.ArrayList;
import java.util.List;

public class GroupRepository {

    private final DatabaseClient dbClient;
    private final GroupDao groupDao;
    private final FieldDao fieldDao;

    public GroupRepository(Context context) {
        this.dbClient = DatabaseClient.getInstance(context);
        this.groupDao = dbClient.groupDao();
        this.fieldDao = dbClient.fieldDao();
    }

    public long insert(Group group) {
        return groupDao.insert(group);
    }

    public long insertWithOrder(Group group) {
        return dbClient.database().runInTransaction(() -> {
            Integer maxOrder = groupDao.getMaxOrderForMachine(group.getMachineId());
            group.setOrder(maxOrder == null ? 0 : maxOrder + 1);
            return groupDao.insert(group);
        });
    }

    public void update(Group group) {
        groupDao.update(group);
    }

    public Group getById(int id) {
        Group g = groupDao.getById(id);
        if (g != null) {
            g.setHasUnread(groupDao.hasUnreadFields(id));
        }
        return g;
    }

    public List<Group> getByMachineId(int machineId) {
        List<GroupWithUnread> rows = groupDao.getByMachineIdWithUnread(machineId);
        List<Group> result = new ArrayList<>(rows.size());
        for (GroupWithUnread row : rows) {
            row.group.setHasUnread(row.hasUnread);
            result.add(row.group);
        }
        return result;
    }

    public void updateOrder(List<Group> groupsInOrder) {
        dbClient.database().runInTransaction(() -> {
            for (int i = 0; i < groupsInOrder.size(); i++) {
                groupDao.updateOrderSingle(groupsInOrder.get(i).getId(), i);
            }
        });
    }

    /**
     * Удаление группы.
     * <p>
     * Раньше поля из удаляемой группы «отцеплялись» с сохранением их старого
     * sort_order, из-за чего в общем списке (ungrouped) могли оказаться поля
     * с одинаковым sort_order, и порядок становился неопределённым.
     * <p>
     * Теперь мы явно проставляем им последовательные sort_order в конце
     * ungrouped-списка (аналогично тому, как это сделано для записей в
     * {@link MachineGroupRepository#delete(int)}).
     */
    public void delete(int groupId) {
        Group group = groupDao.getById(groupId);
        if (group == null) return;
        final int machineId = group.getMachineId();

        dbClient.database().runInTransaction(() -> {
            // 1. Получаем поля группы в текущем порядке
            List<Integer> detachedFieldIds = fieldDao.getGroupFieldIdsOrdered(groupId);

            // 2. Отцепляем каждое, назначая ему порядок в конце ungrouped-списка
            if (!detachedFieldIds.isEmpty()) {
                Integer maxUngrouped = fieldDao.getMaxOrderForUngrouped(machineId);
                int nextOrder = (maxUngrouped == null ? 0 : maxUngrouped + 1);
                long now = System.currentTimeMillis();

                for (Integer fieldId : detachedFieldIds) {
                    // moveToGroup с groupId=0 корректно ставит group_id=0 и sort_order=nextOrder
                    fieldDao.moveToGroup(fieldId, 0, nextOrder, now);
                    nextOrder++;
                }
            }

            // 3. Удаляем саму группу и пересортировываем оставшиеся группы
            groupDao.deleteById(groupId);
            groupDao.reorderGroupsForMachine(machineId);

            // 4. На всякий случай выравниваем порядок ungrouped-полей —
            //    это уберёт возможные «дыры» из прошлых версий, но не сломает
            //    порядок только что добавленных полей (они в конце списка).
            fieldDao.reorderUngrouped(machineId);
        });
    }
}