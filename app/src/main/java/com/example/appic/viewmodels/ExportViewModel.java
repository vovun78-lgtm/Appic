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

public class ExportViewModel extends AndroidViewModel {

    private static final String TAG = "ExportViewModel";

    public enum OperationType {
        EXPORT_ALL,
        EXPORT_FILTERED,
        EXPORT_MACHINE_TO_URI,
        SHARE_MACHINE,
        SHARE_FILTERED,
        EXPORT_GROUP_TO_URI,
        SHARE_GROUP,
        EXPORT_FIELD_TO_URI,
        SHARE_FIELD,
        EXPORT_MACHINE_GROUP_TO_URI,
        SHARE_MACHINE_GROUP
    }

    public static class ExportState {
        public final boolean inProgress;
        public final String message;
        public ExportState(boolean inProgress, String message) {
            this.inProgress = inProgress;
            this.message = message;
        }
    }

    public static class ExportResult {
        public final OperationType operation;
        public final boolean success;
        public final Uri sharedUri;
        public ExportResult(OperationType operation, boolean success, Uri sharedUri) {
            this.operation = operation;
            this.success = success;
            this.sharedUri = sharedUri;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<ExportState> state =
            new MutableLiveData<>(new ExportState(false, null));
    private final MutableLiveData<Event<ExportResult>> resultEvent = new MutableLiveData<>();

    public ExportViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<ExportState> getState() { return state; }
    public LiveData<Event<ExportResult>> getResultEvent() { return resultEvent; }

    public boolean isOperationInProgress() {
        ExportState s = state.getValue();
        return s != null && s.inProgress;
    }

    private interface Work {
        Object execute() throws Exception;
    }

    /** ПАТЧ 9: единая проверка Uri. Возвращает false, если uri == null (уже отправили resultEvent). */
    private boolean checkUri(OperationType op, Uri uri) {
        if (uri == null) {
            Log.w(TAG, "Operation " + op + " rejected: null Uri");
            resultEvent.postValue(new Event<>(new ExportResult(op, false, null)));
            return false;
        }
        return true;
    }

    private void run(OperationType op, String message, Work work) {
        if (isOperationInProgress()) {
            resultEvent.postValue(new Event<>(new ExportResult(op, false, null)));
            return;
        }
        state.setValue(new ExportState(true, message));
        try {
            executor.execute(() -> {
                boolean success = false;
                Uri uri = null;
                try {
                    Object result = work.execute();
                    if (result instanceof Boolean) {
                        success = (Boolean) result;
                    } else if (result instanceof Uri) {
                        uri = (Uri) result;
                        success = uri != null;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Operation " + op + " failed", e);
                } finally {
                    // ПАТЧ 2: state сбрасывается ВСЕГДА, даже если work.execute() кинул Error.
                    state.postValue(new ExportState(false, null));
                    resultEvent.postValue(new Event<>(new ExportResult(op, success, uri)));
                }
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "Operation " + op + " rejected: executor shutdown", e);
            state.postValue(new ExportState(false, null));
            resultEvent.postValue(new Event<>(new ExportResult(op, false, null)));
        }
    }

    public void exportAll(Uri uri) {
        if (!checkUri(OperationType.EXPORT_ALL, uri)) return;
        run(OperationType.EXPORT_ALL, "Экспорт данных...",
                () -> ExportImportHelper.performExport(getApplication(), uri));
    }

    public void exportFiltered(Uri uri, long startDate, long endDate) {
        if (!checkUri(OperationType.EXPORT_FILTERED, uri)) return;
        run(OperationType.EXPORT_FILTERED, "Экспорт данных...",
                () -> ExportImportHelper.performExportFiltered(getApplication(), uri, startDate, endDate));
    }

    public void exportMachineToUri(Uri uri, int machineId) {
        if (!checkUri(OperationType.EXPORT_MACHINE_TO_URI, uri)) return;
        run(OperationType.EXPORT_MACHINE_TO_URI, "Экспорт...",
                () -> ExportImportHelper.performExportMachineToUri(getApplication(), uri, machineId));
    }

    public void exportGroupToUri(Uri uri, int groupId) {
        if (!checkUri(OperationType.EXPORT_GROUP_TO_URI, uri)) return;
        run(OperationType.EXPORT_GROUP_TO_URI, "Экспорт группы...",
                () -> ExportImportHelper.performExportGroupToUri(getApplication(), uri, groupId));
    }

    public void exportFieldToUri(Uri uri, int fieldId) {
        if (!checkUri(OperationType.EXPORT_FIELD_TO_URI, uri)) return;
        run(OperationType.EXPORT_FIELD_TO_URI, "Экспорт записи...",
                () -> ExportImportHelper.performExportFieldToUri(getApplication(), uri, fieldId));
    }

    public void exportMachineGroupToUri(Uri uri, int machineGroupId) {
        if (!checkUri(OperationType.EXPORT_MACHINE_GROUP_TO_URI, uri)) return;
        run(OperationType.EXPORT_MACHINE_GROUP_TO_URI, "Экспорт группы...",
                () -> ExportImportHelper.performExportMachineGroupToUri(getApplication(), uri, machineGroupId));
    }

    public void shareMachine(int machineId, String machineName) {
        run(OperationType.SHARE_MACHINE, "Подготовка архива...",
                () -> ExportImportHelper.exportMachineToTempFile(getApplication(), machineId, machineName));
    }

    public void shareFiltered(long startDate, long endDate, String fileName) {
        run(OperationType.SHARE_FILTERED, "Подготовка архива...",
                () -> ExportImportHelper.exportToTempFileWithName(
                        getApplication(), startDate, endDate, fileName));
    }

    public void shareGroup(int groupId, String groupName) {
        run(OperationType.SHARE_GROUP, "Подготовка архива...",
                () -> ExportImportHelper.exportGroupToTempFile(getApplication(), groupId, groupName));
    }

    public void shareField(int fieldId, String fieldTitle) {
        run(OperationType.SHARE_FIELD, "Подготовка архива...",
                () -> ExportImportHelper.exportFieldToTempFile(getApplication(), fieldId, fieldTitle));
    }

    public void shareMachineGroup(int machineGroupId, String groupName) {
        run(OperationType.SHARE_MACHINE_GROUP, "Подготовка архива...",
                () -> ExportImportHelper.exportMachineGroupToTempFile(
                        getApplication(), machineGroupId, groupName));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}