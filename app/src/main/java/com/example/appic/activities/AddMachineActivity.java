package com.example.appic.activities;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.example.appic.R;
import com.example.appic.utils.MaterialProgressDialog;
import com.example.appic.utils.TermsHelper;
import com.example.appic.utils.TextCapitalizer;
import com.example.appic.utils.ThemeHelper;
import com.example.appic.viewmodels.AddMachineViewModel;

public class AddMachineActivity extends BaseActivity {

    private EditText etName;
    private Button btnSave;
    private int machineId = -1;
    private int groupId = 0;

    private MaterialProgressDialog progressDialog;
    private AddMachineViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_machine);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.applyToToolbar(toolbar);

        etName = findViewById(R.id.et_machine_name);
        btnSave = findViewById(R.id.btn_save);

        ThemeHelper.applyToEditText(etName);
        TextCapitalizer.apply(etName);

        Intent intent = getIntent();
        machineId = intent.getIntExtra("machine_id", -1);
        groupId = intent.getIntExtra("machine_group_id", 0);
        String currentName = intent.getStringExtra("machine_name");
        if (machineId != -1 && currentName != null) {
            etName.setText(currentName);
        }

        if (getSupportActionBar() != null) {
            String machineSingle = TermsHelper.getMachineSingle(this).toLowerCase();
            getSupportActionBar().setTitle(machineId == -1
                    ? getString(R.string.title_add_machine) + " " + machineSingle
                    : getString(R.string.title_edit_machine) + " " + machineSingle);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        viewModel = new ViewModelProvider(this).get(AddMachineViewModel.class);

        viewModel.getState().observe(this, s -> {
            if (s == null) return;
            if (s.inProgress) {
                btnSave.setEnabled(false);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                showProgress(getString(R.string.progress_saving));
            } else if (s.finished) {
                btnSave.setEnabled(true);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                dismissProgress();
                if (s.success) {
                    setResult(RESULT_OK);
                    finish();
                }
            }
        });

        viewModel.getErrorEvent().observe(this, event -> {
            if (event == null) return;
            String msg = event.getContentIfNotHandled();
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.validation_fill_title,
                        TermsHelper.getMachineSingle(this).toLowerCase()), Toast.LENGTH_SHORT).show();
                return;
            }
            name = TextCapitalizer.capitalizeSentences(name);
            viewModel.save(machineId, name, groupId);
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) ThemeHelper.applyToToolbar(toolbar);
        ThemeHelper.applyToEditText(etName);
    }

    @Override
    protected void onDestroy() {
        dismissProgress();
        super.onDestroy();
    }

    private void showProgress(String message) {
        if (isFinishing() || isDestroyed()) return;
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = new MaterialProgressDialog(this);
        progressDialog.setMessage(message);
        progressDialog.setCancelable(false);
        try { progressDialog.show(); } catch (Exception ignored) {}
    }

    private void dismissProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try { progressDialog.dismiss(); } catch (Exception ignored) {}
        }
        progressDialog = null;
    }

    public static Intent newIntent(android.content.Context context, int machineId, String machineName) {
        Intent i = new Intent(context, AddMachineActivity.class);
        i.putExtra("machine_id", machineId);
        i.putExtra("machine_name", machineName);
        i.putExtra("machine_group_id", 0);
        return i;
    }

    public static Intent newIntentInGroup(android.content.Context context, int machineGroupId) {
        Intent i = new Intent(context, AddMachineActivity.class);
        i.putExtra("machine_id", -1);
        i.putExtra("machine_name", "");
        i.putExtra("machine_group_id", machineGroupId);
        return i;
    }
}