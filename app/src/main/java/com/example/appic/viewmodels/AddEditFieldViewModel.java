package com.example.appic.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.appic.database.DatabaseClient;
import com.example.appic.database.models.Field;
import com.example.appic.database.models.Media;
import com.example.appic.database.repository.FieldRepository;
import com.example.appic.utils.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class AddEditFieldViewModel extends AndroidViewModel {

    private static final String TAG = "AddEditFieldVM";

    private final FieldRepository repo;
    private final DatabaseClient dbClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<SaveState> state = new MutableLiveData<>(SaveState.idle());
    private final MutableLiveData<Event<String>> errorEvent = new MutableLiveData<>();

    public AddEditFieldViewModel(@NonNull Application app) {
        super(app);
        this.repo = new FieldRepository(app);
        this.dbClient = DatabaseClient.getInstance(app);
    }

    public LiveData<SaveState> getState() { return state; }
    public LiveData<Event<String>> getErrorEvent() { return errorEvent; }

    public void save(int existingFieldId, int machineId, int groupId,
                     String title, String text, String customValuesJson, List<Media> media) {
        SaveState cur = state.getValue();
        if (cur != null && cur.inProgress) return;

        state.setValue(SaveState.running());

        final List<Media> mediaSnapshot = new ArrayList<>(media);

        try {
            executor.execute(() -> {
                try {
                    final int[] savedFieldId = {existingFieldId};
                    // ПАТЧ 1: индекс в mediaSnapshot -> id в БД.
                    // Индекс (а не объект Media) используется намеренно: при повороте
                    // экрана Activity пересоздаётся, и объекты Media в новом mediaList —
                    // другие. Индекс стабилен, так как список восстанавливается в том же
                    // порядке из savedInstanceState.
                    final Map<Integer, Integer> newIdsByIndex = new HashMap<>();

                    dbClient.database().runInTransaction(() -> {
                        if (existingFieldId == -1) {
                            long now = System.currentTimeMillis();
                            int order = repo.getNextOrder(machineId, groupId);
                            Field f = new Field(0, machineId, title, text, now, now, order, groupId, true, customValuesJson);
                            long newId = repo.insert(f);
                            savedFieldId[0] = (int) newId;
                        } else {
                            Field existing = repo.getById(existingFieldId);
                            if (existing == null) throw new IllegalStateException("FIELD_NOT_FOUND");
                            Field f = new Field(existingFieldId, machineId, title, text,
                                    existing.getCreatedAt(), System.currentTimeMillis(),
                                    existing.getOrder(), existing.getGroupId(), existing.isRead(), customValuesJson);
                            repo.update(f);
                        }

                        for (int i = 0; i < mediaSnapshot.size(); i++) {
                            Media m = mediaSnapshot.get(i);
                            if (m.getId() == 0) {
                                Media fresh = new Media(0, savedFieldId[0], m.getType(), m.getPath());
                                long id = repo.insertMedia(fresh);
                                newIdsByIndex.put(i, (int) id);
                            }
                        }
                    });

                    state.postValue(SaveState.success(savedFieldId[0], newIdsByIndex));
                } catch (Exception e) {
                    Log.e(TAG, "save failed", e);
                    String msg = "FIELD_NOT_FOUND".equals(e.getMessage())
                            ? "Ошибка: поле не найдено"
                            : "Ошибка сохранения: " + (e.getMessage() == null ? "неизвестно" : e.getMessage());
                    state.postValue(SaveState.finished(false));
                    errorEvent.postValue(new Event<>(msg));
                }
            });
        } catch (RejectedExecutionException e) {
            state.postValue(SaveState.finished(false));
            errorEvent.postValue(new Event<>("Сохранение невозможно"));
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    public static class SaveState {
        public final boolean inProgress;
        public final boolean finished;
        public final boolean success;
        public final int fieldId;
        public final Map<Integer, Integer> newMediaIdsByIndex;

        private SaveState(boolean inProgress, boolean finished, boolean success,
                          int fieldId, Map<Integer, Integer> newMediaIdsByIndex) {
            this.inProgress = inProgress;
            this.finished = finished;
            this.success = success;
            this.fieldId = fieldId;
            this.newMediaIdsByIndex = newMediaIdsByIndex;
        }

        static SaveState idle() { return new SaveState(false, false, false, -1, null); }
        static SaveState running() { return new SaveState(true, false, false, -1, null); }
        static SaveState success(int fieldId, Map<Integer, Integer> ids) {
            return new SaveState(false, true, true, fieldId, ids);
        }
        static SaveState finished(boolean s) { return new SaveState(false, true, s, -1, null); }
    }
}