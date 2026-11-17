package com.example.appic.viewmodels;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.appic.utils.Event;
import com.example.appic.utils.ExportImportHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class ImportViewModel extends AndroidViewModel {

    private static final String TAG = "ImportViewModel";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<ImportUiState> state =
            new MutableLiveData<>(new ImportUiState(false, 0));
    private final MutableLiveData<Event<Boolean>> finishedEvent = new MutableLiveData<>();

    public ImportViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<ImportUiState> getState() { return state; }
    public LiveData<Event<Boolean>> getFinishedEvent() { return finishedEvent; }

    public boolean isImportInProgress() {
        ImportUiState s = state.getValue();
        return s != null && s.inProgress;
    }

    public void startImport(Uri uri) {
        if (uri == null) {
            Log.w(TAG, "startImport rejected: null Uri");
            finishedEvent.postValue(new Event<>(false));
            return;
        }
        ImportUiState current = state.getValue();
        if (current != null && current.inProgress) return;

        state.setValue(new ImportUiState(true, 0));

        try {
            executor.execute(() -> {
                boolean success = false;
                try {
                    success = ExportImportHelper.performImport(
                            getApplication(),
                            uri,
                            new ExportImportHelper.ImportProgressListener() {
                                @Override
                                public void onProgressUpdate(int percent) {
                                    state.postValue(new ImportUiState(true, percent));
                                }

                                @Override
                                public void onFinished(boolean s) {
                                    // ПАТЧ 8: завершение обрабатывается через возвращаемое
                                    // значение performImport() и finishedEvent ниже.
                                    // onFinished здесь намеренно пуст — единственный источник
                                    // истины о результате — return value.
                                }
                            },
                            ExportImportHelper.IMPORT_MODE_MERGE);
                } catch (Exception e) {
                    Log.e(TAG, "Import failed", e);
                } finally {
                    // ПАТЧ 2: state сбрасывается всегда, даже при Error.
                    state.postValue(new ImportUiState(false, success ? 100 : 0));
                    finishedEvent.postValue(new Event<>(success));
                }
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "Import rejected: executor already shutdown", e);
            state.postValue(new ImportUiState(false, 0));
            finishedEvent.postValue(new Event<>(false));
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    public static class ImportUiState {
        public final boolean inProgress;
        public final int progress;
        public ImportUiState(boolean inProgress, int progress) {
            this.inProgress = inProgress;
            this.progress = progress;
        }
    }
}