package com.example.appic.database;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.appic.BuildConfig;
import com.example.appic.database.dao.FieldDao;
import com.example.appic.database.dao.FieldTemplateDao;
import com.example.appic.database.dao.GroupDao;
import com.example.appic.database.dao.MachineDao;
import com.example.appic.database.dao.MachineGroupDao;
import com.example.appic.database.dao.MediaDao;
import com.example.appic.database.dao.ReportDefinitionDao;
import com.example.appic.database.models.Field;
import com.example.appic.database.models.FieldTemplate;
import com.example.appic.database.models.Group;
import com.example.appic.database.models.Machine;
import com.example.appic.database.models.MachineGroup;
import com.example.appic.database.models.Media;
import com.example.appic.database.models.ReportDefinition;

@Database(
        entities = {Field.class, Group.class, Machine.class, MachineGroup.class,
                Media.class, FieldTemplate.class, ReportDefinition.class},
        version = 1,
        // ИСПРАВЛЕНО (3.2.4): exportSchema=true согласовано с
        // app/build.gradle (room.schemaLocation = "$projectDir/schemas").
        // Room будет сохранять 1.json в app/schemas/com.example.appic.database.AppDatabase/
        // при сборке — это база для будущих миграций.
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String TAG = "AppDatabase";
    public static final String DATABASE_NAME = "appic.db";
    private static volatile AppDatabase instance;

    public abstract FieldDao fieldDao();
    public abstract GroupDao groupDao();
    public abstract MachineDao machineDao();
    public abstract MachineGroupDao machineGroupDao();
    public abstract MediaDao mediaDao();
    public abstract FieldTemplateDao fieldTemplateDao();
    public abstract ReportDefinitionDao reportDefinitionDao();

    public static boolean columnExists(SupportSQLiteDatabase db, String tableName, String columnName) {
        try (Cursor cursor = db.query("PRAGMA table_info(" + tableName + ")")) {
            int nameIdx = cursor.getColumnIndex("name");
            if (nameIdx < 0) return false;
            while (cursor.moveToNext()) {
                if (columnName.equals(cursor.getString(nameIdx))) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static final Callback FK_CALLBACK = new Callback() {
        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            try {
                db.execSQL("PRAGMA foreign_keys=ON");
            } catch (Exception e) {
                Log.w(TAG, "Failed to enforce PRAGMA foreign_keys=ON", e);
            }
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    RoomDatabase.Builder<AppDatabase> builder = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME)
                            .addCallback(FK_CALLBACK);

                    // РЕЛИЗ: fallbackToDestructiveMigration() НЕ применяется.
                    // DEBUG: разрешён, чтобы во время разработки не писать миграцию
                    // под каждое изменение схемы. Для релиза схема должна быть
                    // стабильна (version = 1 до первого публичного релиза).
                    if (BuildConfig.DEBUG) {
                        builder.fallbackToDestructiveMigration();
                    }

                    instance = builder.build();
                }
            }
        }
        return instance;
    }
}