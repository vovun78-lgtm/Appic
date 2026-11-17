package com.example.appic.database;

import android.content.Context;

import com.example.appic.database.dao.FieldDao;
import com.example.appic.database.dao.FieldTemplateDao;
import com.example.appic.database.dao.GroupDao;
import com.example.appic.database.dao.MachineDao;
import com.example.appic.database.dao.MachineGroupDao;
import com.example.appic.database.dao.MediaDao;
import com.example.appic.database.dao.ReportDefinitionDao;

public final class DatabaseClient {

    private static volatile DatabaseClient instance;
    private final AppDatabase db;

    private DatabaseClient(Context context) {
        this.db = AppDatabase.getInstance(context);
    }

    public static DatabaseClient getInstance(Context context) {
        if (instance == null) {
            synchronized (DatabaseClient.class) {
                if (instance == null) {
                    instance = new DatabaseClient(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public FieldDao fieldDao() { return db.fieldDao(); }
    public GroupDao groupDao() { return db.groupDao(); }
    public MachineDao machineDao() { return db.machineDao(); }
    public MachineGroupDao machineGroupDao() { return db.machineGroupDao(); }
    public MediaDao mediaDao() { return db.mediaDao(); }
    public FieldTemplateDao fieldTemplateDao() { return db.fieldTemplateDao(); }
    public ReportDefinitionDao reportDefinitionDao() { return db.reportDefinitionDao(); }

    public AppDatabase database() { return db; }
}