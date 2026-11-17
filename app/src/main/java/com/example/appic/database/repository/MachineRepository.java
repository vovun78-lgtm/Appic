package com.example.appic.database.repository;

import android.content.Context;

import com.example.appic.database.DatabaseClient;
import com.example.appic.database.dao.FieldDao;
import com.example.appic.database.dao.GroupDao;
import com.example.appic.database.dao.MachineDao;
import com.example.appic.database.dao.MediaDao;
import com.example.appic.database.models.Machine;
import com.example.appic.database.models.MachineWithUnread;
import com.example.appic.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;

public class MachineRepository {

    private final DatabaseClient dbClient;
    private final MachineDao machineDao;
    private final FieldDao fieldDao;
    private final GroupDao groupDao;
    private final MediaDao mediaDao;

    public MachineRepository(Context context) {
        this.dbClient = DatabaseClient.getInstance(context);
        this.machineDao = dbClient.machineDao();
        this.fieldDao = dbClient.fieldDao();
        this.groupDao = dbClient.groupDao();
        this.mediaDao = dbClient.mediaDao();
    }

    public long insert(Machine machine) {
        return machineDao.insert(machine);
    }

    public long insertWithOrder(Machine machine) {
        return dbClient.database().runInTransaction(() -> {
            Integer maxOrder = machineDao.getMaxOrderForGroup(machine.getGroupId());
            machine.setOrder(maxOrder == null ? 0 : maxOrder + 1);
            return machineDao.insert(machine);
        });
    }

    public void update(Machine machine) {
        machineDao.update(machine);
    }

    /**
     * ПАТЧ 3: возвращает false, если объекта с таким id больше нет.
     * Это позволяет вызывающему коду (AddMachineViewModel) отличить
     * «сохранено» от «объект удалён в другом окне/экране».
     */
    public boolean updateName(int machineId, String newName) {
        Machine existing = machineDao.getById(machineId);
        if (existing == null) return false;
        existing.setName(newName);
        machineDao.update(existing);
        return true;
    }

    public Machine getById(int id) {
        return machineDao.getById(id);
    }

    public List<Machine> getAll() {
        List<MachineWithUnread> rows = machineDao.getAllWithUnread();
        List<Machine> result = new ArrayList<>(rows.size());
        for (MachineWithUnread row : rows) {
            row.machine.setHasUnread(row.hasUnread);
            result.add(row.machine);
        }
        return result;
    }

    public List<Machine> getUngrouped() {
        List<MachineWithUnread> rows = machineDao.getUngroupedWithUnread();
        List<Machine> result = new ArrayList<>(rows.size());
        for (MachineWithUnread row : rows) {
            row.machine.setHasUnread(row.hasUnread);
            result.add(row.machine);
        }
        return result;
    }

    public List<Machine> getByGroupId(int groupId) {
        List<MachineWithUnread> rows = machineDao.getByGroupIdWithUnread(groupId);
        List<Machine> result = new ArrayList<>(rows.size());
        for (MachineWithUnread row : rows) {
            row.machine.setHasUnread(row.hasUnread);
            result.add(row.machine);
        }
        return result;
    }

    public void updateOrder(List<Machine> machinesInOrder) {
        dbClient.database().runInTransaction(() -> {
            for (int i = 0; i < machinesInOrder.size(); i++) {
                machineDao.updateOrderSingle(machinesInOrder.get(i).getId(), i);
            }
        });
    }

    public void updateOrderInGroup(List<Machine> machinesInOrder, int groupId) {
        dbClient.database().runInTransaction(() -> {
            int i = 0;
            for (Machine m : machinesInOrder) {
                if (m.getGroupId() == groupId) {
                    machineDao.updateOrderSingle(m.getId(), i);
                    i++;
                }
            }
        });
    }

    public void moveToGroup(int machineId, int groupId) {
        dbClient.database().runInTransaction(() -> {
            Integer maxOrder = machineDao.getMaxOrderForGroup(groupId);
            int order = maxOrder == null ? 0 : maxOrder + 1;
            machineDao.moveToGroup(machineId, groupId, order);
        });
    }

    public void delete(int machineId) {
        List<String> paths = mediaDao.getPathsByMachineId(machineId);
        List<Integer> fieldIds = fieldDao.getFieldIdsByMachineId(machineId);

        Machine machine = machineDao.getById(machineId);
        final int groupId = machine != null ? machine.getGroupId() : 0;

        dbClient.database().runInTransaction(() -> {
            if (!fieldIds.isEmpty()) {
                mediaDao.deleteByFieldIds(fieldIds);
            }
            fieldDao.deleteByMachineId(machineId);
            groupDao.deleteByMachineId(machineId);
            machineDao.deleteById(machineId);
            if (groupId == 0) {
                machineDao.reorderAll();
            } else {
                machineDao.reorderGroup(groupId);
            }
        });

        for (String p : paths) FileUtils.deleteFile(p);
    }
}