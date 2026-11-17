package com.example.appic.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.appic.database.models.Machine;
import com.example.appic.database.repository.MachineRepository;
import com.example.appic.utils.Event;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class AddMachineViewModel extends AndroidViewModel {

    private static final String TAG = "AddMachineVM";

    private final MachineRepository repo;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<SaveState> state = new MutableLiveData<>(SaveState.idle());
    private final MutableLiveData<Event<String>> errorEvent = new MutableLiveData<>();

    public AddMachineViewModel(@NonNull Application app) {
        super(app);
        this.repo = new MachineRepository(app);
    }

    public LiveData<SaveState> getState() { return state; }
    public LiveData<Event<String>> getErrorEvent() { return errorEvent; }

    public void save(int machineId, String name, int groupId) {
        SaveState cur = state.getValue();
        if (cur != null && cur.inProgress) return;

        state.setValue(SaveState.running());
        try {
            executor.execute(() -> {
                boolean ok = false;
                String errorMsg = null;
                try {
                    if (machineId == -1) {
                        repo.insertWithOrder(new Machine(0, name, 0, groupId));
                        ok = true;
                    } else {
                        // ПАТЧ 3: проверяем, что объект не был удалён.
                        ok = repo.updateName(machineId, name);
                        if (!ok) {
                            errorMsg = "Объект не найден — возможно, он был удалён";
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "save failed", e);
                    errorMsg = "Не удалось сохранить объект";
                    ok = false;
                }
                state.postValue(SaveState.finished(ok));
                if (errorMsg != null) {
                    errorEvent.postValue(new Event<>(errorMsg));
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

        private SaveState(boolean inProgress, boolean finished, boolean success) {
            this.inProgress = inProgress;
            this.finished = finished;
            this.success = success;
        }

        static SaveState idle() { return new SaveState(false, false, false); }
        static SaveState running() { return new SaveState(true, false, false); }
        static SaveState finished(boolean s) { return new SaveState(false, true, s); }
    }
}