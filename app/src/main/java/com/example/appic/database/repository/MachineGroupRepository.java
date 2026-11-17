package com.example.appic.database.repository;

import android.content.Context;

import com.example.appic.database.DatabaseClient;
import com.example.appic.database.dao.MachineDao;
import com.example.appic.database.dao.MachineGroupDao;
import com.example.appic.database.models.Machine;
import com.example.appic.database.models.MachineGroup;

import java.util.List;

public class MachineGroupRepository {

    private final DatabaseClient dbClient;
    private final MachineGroupDao machineGroupDao;
    private final MachineDao machineDao;

    public MachineGroupRepository(Context context) {
        this.dbClient = DatabaseClient.getInstance(context);
        this.machineGroupDao = dbClient.machineGroupDao();
        this.machineDao = dbClient.machineDao();
    }

    public long insert(MachineGroup group) {
        return machineGroupDao.insert(group);
    }

    public long insertWithOrder(MachineGroup group) {
        return dbClient.database().runInTransaction(() -> {
            Integer maxOrder = machineGroupDao.getMaxOrder();
            group.setOrder(maxOrder == null ? 0 : maxOrder + 1);
            return machineGroupDao.insert(group);
        });
    }

    public void update(MachineGroup group) {
        machineGroupDao.update(group);
    }

    public MachineGroup getById(int id) {
        return machineGroupDao.getById(id);
    }

    public List<MachineGroup> getAll() {
        return machineGroupDao.getAll();
    }

    public void updateOrder(List<MachineGroup> groupsInOrder) {
        dbClient.database().runInTransaction(() -> {
            for (int i = 0; i < groupsInOrder.size(); i++) {
                machineGroupDao.updateOrderSingle(groupsInOrder.get(i).getId(), i);
            }
        });
    }

    /**
     * Удаление группы : становятся «без группы»,
     * попадают в конец общего списка (sort_order = max+1 в ungrouped).
     * Группы пересортировываются.
     */
    public void delete(int groupId) {
        dbClient.database().runInTransaction(() -> {
            List<Machine> machinesInGroup = machineDao.getByGroupId(groupId);
            Integer maxUngrouped = machineDao.getMaxOrderForGroup(0);
            int nextOrder = maxUngrouped == null ? 0 : maxUngrouped + 1;
            for (Machine m : machinesInGroup) {
                machineDao.moveToGroup(m.getId(), 0, nextOrder);
                nextOrder++;
            }
            machineGroupDao.deleteById(groupId);
            machineGroupDao.reorderAll();
        });
    }
}