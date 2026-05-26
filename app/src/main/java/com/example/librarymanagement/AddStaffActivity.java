package com.example.librarymanagement;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddStaffActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPassword;
    private RadioGroup rgRole;
    private RadioButton rbLibrarian, rbAssistant;
    private CheckBox cbBooks, cbStudents, cbIssue, cbFine;
    private Button btnSave;
    private TextView tvHeader;
    private DatabaseHelper db;
    private FirebaseFirestore firestore;
    private FirebaseAuth mAuth;
    private boolean isEditMode = false;
    private int staffId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_staff);

        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        etName = findViewById(R.id.etStaffName);
        etEmail = findViewById(R.id.etStaffEmail);
        etPassword = findViewById(R.id.etStaffPassword);
        rgRole = findViewById(R.id.rgRole);
        rbLibrarian = findViewById(R.id.rbLibrarian);
        rbAssistant = findViewById(R.id.rbAssistant);
        
        cbBooks = findViewById(R.id.cbBooks);
        cbStudents = findViewById(R.id.cbStudents);
        cbIssue = findViewById(R.id.cbIssue);
        cbFine = findViewById(R.id.cbFine);
        
        btnSave = findViewById(R.id.btnSaveStaff);
        
        tvHeader = findViewById(R.id.tvAddStaffHeader);

        isEditMode = getIntent().getBooleanExtra("EDIT_MODE", false);
        if (isEditMode) {
            staffId = getIntent().getIntExtra("STAFF_ID", -1);
            loadStaffData();
            btnSave.setText("Update Staff Account");
            if (tvHeader != null) tvHeader.setText("Edit Staff Member");
        }

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            
            int checkedId = rgRole.getCheckedRadioButtonId();
            String role = "librarian";
            if (checkedId == R.id.rbAssistant) role = "assistant";

            if (name.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Name and Email are required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!isEditMode && password.isEmpty()) {
                Toast.makeText(this, "Password is required for new staff", Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder tasks = new StringBuilder();
            if (cbBooks.isChecked()) tasks.append("Books,");
            if (cbStudents.isChecked()) tasks.append("Students,");
            if (cbIssue.isChecked()) tasks.append("Issue,");
            if (cbFine.isChecked()) tasks.append("Fine,");

            String finalTasks = tasks.toString();

            if (isEditMode) {
                if (db.updateStaff(staffId, name, email, password, role, finalTasks)) {
                    updateStaffInFirestore(name, email, role, finalTasks);
                    Toast.makeText(this, "Staff Updated Successfully", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, "Update Failed", Toast.LENGTH_SHORT).show();
                }
            } else {
                btnSave.setEnabled(false);
                if (db.insertStaff(name, email, password, role, finalTasks)) {
                    createStaffAuth(name, email, password, role, finalTasks);
                } else {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Creation Failed (Email exists?)", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void createStaffAuth(String name, String email, String password, String role, String tasks) {
        // Create secondary FirebaseApp to avoid logging out the current Admin
        FirebaseOptions options = FirebaseApp.getInstance().getOptions();
        FirebaseApp tempApp;
        try {
            tempApp = FirebaseApp.initializeApp(this, options, "secondary");
        } catch (IllegalStateException e) {
            tempApp = FirebaseApp.getInstance("secondary");
        }
        
        FirebaseAuth tempAuth = FirebaseAuth.getInstance(tempApp);

        tempAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        syncStaffToFirestore(name, email, role, tasks);
                        Toast.makeText(this, "Staff Account Created Successfully", Toast.LENGTH_SHORT).show();
                        FirebaseApp.getInstance("secondary").delete();
                        finish();
                    } else {
                        btnSave.setEnabled(true);
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Log.e("AddStaff", "Auth Creation Failed: " + error);
                        Toast.makeText(this, "Authentication Failed: " + error, Toast.LENGTH_LONG).show();
                        FirebaseApp.getInstance("secondary").delete();
                    }
                });
    }

    private void syncStaffToFirestore(String name, String email, String role, String tasks) {
        Map<String, Object> staff = new HashMap<>();
        staff.put("name", name);
        staff.put("email", email);
        staff.put("role", role);
        staff.put("assigned_tasks", tasks);
        staff.put("status", "Active");
        staff.put("totalTimeSpent", 0);
        
        firestore.collection("users").document(email).set(staff);
    }

    private void updateStaffInFirestore(String name, String email, String role, String tasks) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("role", role);
        updates.put("assigned_tasks", tasks);
        
        firestore.collection("users").document(email).update(updates);
    }

    private void loadStaffData() {
        etName.setText(getIntent().getStringExtra("NAME"));
        etEmail.setText(getIntent().getStringExtra("EMAIL"));
        etEmail.setEnabled(false);
        
        String role = getIntent().getStringExtra("ROLE");
        if ("assistant".equalsIgnoreCase(role)) {
            rbAssistant.setChecked(true);
        } else {
            rbLibrarian.setChecked(true);
        }

        String tasks = getIntent().getStringExtra("TASKS");
        if (tasks != null) {
            cbBooks.setChecked(tasks.contains("Books"));
            cbStudents.setChecked(tasks.contains("Students"));
            cbIssue.setChecked(tasks.contains("Issue"));
            cbFine.setChecked(tasks.contains("Fine"));
        }
    }
}
