package com.example.librarymanagement;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvSignup, tvForgotPassword;
    private CheckBox cbRemember, cbTerms;
    private ImageView ivVisibility;
    private FloatingActionButton fabHelp;
    private boolean isPasswordVisible = false;

    private DatabaseHelper db;
    private SharedPreferences sharedPreferences;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvSignup = findViewById(R.id.tvSignup);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        cbRemember = findViewById(R.id.cbRemember);
        cbTerms = findViewById(R.id.cbTerms);
        ivVisibility = findViewById(R.id.ivVisibility);
        fabHelp = findViewById(R.id.fabHelp);

        db = new DatabaseHelper(this);
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        // Auto-login logic
        if (sharedPreferences.getBoolean("isLoggedIn", false)) {
            String role = sharedPreferences.getString("role", "student");
            String name = sharedPreferences.getString("name", "User");
            String email = sharedPreferences.getString("email", "");
            navigateToDashboard(role, name, email);
        }

        ivVisibility.setOnClickListener(v -> {
            if (isPasswordVisible) {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                ivVisibility.setImageResource(android.R.drawable.ic_menu_view);
                isPasswordVisible = false;
            } else {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                ivVisibility.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                isPasswordVisible = true;
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if(email.isEmpty() || password.isEmpty()){
                Toast.makeText(LoginActivity.this,"Please enter details",Toast.LENGTH_SHORT).show();
                return;
            }

            if (!cbTerms.isChecked()) {
                Toast.makeText(this, "Please accept Terms and Conditions to continue", Toast.LENGTH_SHORT).show();
                return;
            }

            btnLogin.setEnabled(false);
            
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                fetchUserRoleAndNavigate(user.getEmail(), password);
                            }
                        } else {
                            loginOffline(email, password);
                        }
                    });
        });

        tvSignup.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        tvForgotPassword.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        fabHelp.setOnClickListener(v -> showHelpSupportDialog());
    }

    private void fetchUserRoleAndNavigate(String email, String password) {
        firestore.collection("users").document(email)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String role = doc.getString("role");
                        String name = doc.getString("name");
                        String studentId = doc.getString("student_id");
                        String tasks = doc.getString("assigned_tasks");
                        if (tasks == null) tasks = "";
                        
                        saveSession(email, name, role);

                        // Sync user to local DB with tasks and status
                        db.insertUserFull(name, email, password, role, 
                                studentId != null ? studentId : "ADM-000", "Online", tasks);

                        if (isStaff(role)) {
                            db.recordLogin(email);
                            // Update Online status in Firestore
                            firestore.collection("users").document(email).update("status", "Online");
                        }
                        
                        Toast.makeText(this, "Online Login Successful", Toast.LENGTH_SHORT).show();
                        navigateToDashboard(role, name, email);
                    } else {
                        loginOffline(email, password); 
                    }
                })
                .addOnFailureListener(e -> loginOffline(email, password));
    }

    private void loginOffline(String email, String password) {
        String role = password.isEmpty() ? db.getUserRole(email) : db.checkUser(email, password);

        if (role != null && !role.isEmpty()) {
            String name = db.getUserName(email);
            saveSession(email, name, role);
            if (isStaff(role)) {
                db.recordLogin(email);
                firestore.collection("users").document(email).update("status", "Online");
            }
            navigateToDashboard(role, name, email);
            Toast.makeText(this, "Logged in Locally", Toast.LENGTH_SHORT).show();
        } else {
            btnLogin.setEnabled(true);
            Toast.makeText(this, "Login Failed. Check connection or credentials.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSession(String email, String name, String role) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (cbRemember.isChecked()) {
            editor.putBoolean("isLoggedIn", true);
        } else {
            editor.putBoolean("isLoggedIn", false);
        }
        editor.putString("email", email);
        editor.putString("name", name);
        editor.putString("role", role);
        editor.apply();
    }

    private void showHelpSupportDialog() {
        String[] options = {"Contact Librarian", "Reset Password Help", "App Guide / FAQs", "Report an Issue"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("SmartLibrary Support")
                .setIcon(android.R.drawable.ic_menu_help)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: contactSupport(); break;
                        case 1: startActivity(new Intent(this, ForgotPasswordActivity.class)); break;
                        case 2: showAppGuide(); break;
                        case 3: sendReportEmail(); break;
                    }
                })
                .setNegativeButton("Close", null).show();
    }

    private void contactSupport() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Contact Support")
                .setMessage("Main Librarian: Admin\nEmail: maravisatyam266@gmail.com\nPhone: +91 9301872307")
                .setPositiveButton("Call Now", (dialog, which) -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:9301872307"))))
                .setNegativeButton("Email", (dialog, which) -> sendReportEmail()).show();
    }

    private void sendReportEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:maravisatyam266@gmail.com"));
        intent.putExtra(Intent.EXTRA_SUBJECT, "SmartLibrary - Issue Report");
        try { startActivity(intent); } catch (Exception e) {}
    }

    private void showAppGuide() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("App Usage Guide")
                .setMessage("1. Online Login ensures live sync.\n2. Browse Books to find details.\n3. Request Book for approval.\n4. View 'My Books' after Librarian approval.")
                .setPositiveButton("Got it", null).show();
    }

    private boolean isStaff(String role) {
        return "librarian".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role);
    }

    private void navigateToDashboard(String role, String name, String email) {
        Intent intent = isStaff(role) ? new Intent(this, AdminDashboardActivity.class) : new Intent(this, StudentDashboardActivity.class);
        intent.putExtra("NAME", name);
        intent.putExtra("EMAIL", email);
        intent.putExtra("ROLE", role);
        startActivity(intent);
        finish();
    }
}
