package com.example.librarymanagement;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    TextView tvName, tvEmail, tvId, tvFine, tvDigitalTitle, tvRole;
    ImageView ivProfileImage, ivQRCode, ivEditName;
    MaterialCardView cvQRCode, cardProfileId, cardProfileFine;
    View llStudentStats;
    MaterialButton btnSecurity, btnBack, btnChangeImage, btnLogout;
    SwitchMaterial switchDarkMode;
    DatabaseHelper db;
    String userEmail, loggedInUserEmail;
    SharedPreferences sharedPreferences;
    boolean isViewingOwnProfile = true;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        ivProfileImage.setImageBitmap(bitmap);
                        
                        byte[] imageBytes = getBytesFromBitmap(bitmap);
                        if (db.updateUserProfileImage(userEmail, imageBytes)) {
                            Toast.makeText(this, "Profile Image Updated", Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("dark_mode", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = new DatabaseHelper(this);
        initViews();
        
        loggedInUserEmail = sharedPreferences.getString("email", "");
        
        // Fix: Check if an email was passed via intent (e.g. from Manage Students)
        String intentEmail = getIntent().getStringExtra("EMAIL");
        if (intentEmail != null && !intentEmail.isEmpty()) {
            userEmail = intentEmail;
            isViewingOwnProfile = userEmail.equalsIgnoreCase(loggedInUserEmail);
        } else {
            userEmail = loggedInUserEmail;
            isViewingOwnProfile = true;
        }

        loadUserData();
        setupInteractions();
    }

    private void initViews() {
        tvName = findViewById(R.id.tvProfileName);
        tvEmail = findViewById(R.id.tvProfileEmail);
        tvId = findViewById(R.id.tvProfileId);
        tvFine = findViewById(R.id.tvProfileFine);
        tvDigitalTitle = findViewById(R.id.tvDigitalTitle);
        tvRole = findViewById(R.id.tvPortalType);
        
        ivProfileImage = findViewById(R.id.ivProfileImage);
        ivQRCode = findViewById(R.id.ivQRCode);
        cvQRCode = findViewById(R.id.cvQRCode);
        ivEditName = findViewById(R.id.ivVisibility);

        llStudentStats = findViewById(R.id.llStudentStats);
        btnChangeImage = findViewById(R.id.btnChangeProfileImage);
        btnSecurity = findViewById(R.id.btnSecurity);
        btnBack = findViewById(R.id.btnBack);
        btnLogout = findViewById(R.id.btnLogout);
        switchDarkMode = findViewById(R.id.switchDarkMode);
    }

    private void setupInteractions() {
        if (!isViewingOwnProfile) {
            // Hide personal controls when viewing someone else's profile
            if (btnChangeImage != null) btnChangeImage.setVisibility(View.GONE);
            if (ivEditName != null) ivEditName.setVisibility(View.GONE);
            if (btnSecurity != null) btnSecurity.setVisibility(View.GONE);
            if (btnLogout != null) btnLogout.setVisibility(View.GONE);
            // Hide settings related switches if they exist
            if (switchDarkMode != null) switchDarkMode.setVisibility(View.GONE);
        } else {
            btnChangeImage.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                imagePickerLauncher.launch(intent);
            });

            if (ivEditName != null) {
                ivEditName.setOnClickListener(v -> showEditNameDialog());
            }

            btnSecurity.setOnClickListener(v -> startActivity(new Intent(this, SecurityActivity.class)));
            
            if (btnLogout != null) {
                btnLogout.setOnClickListener(v -> showLogoutConfirm());
            }

            switchDarkMode.setChecked(sharedPreferences.getBoolean("dark_mode", false));
            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sharedPreferences.edit().putBoolean("dark_mode", isChecked).apply();
                AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            });
        }

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadUserData() {
        String name = db.getUserName(userEmail);
        String role = db.getUserRole(userEmail);
        
        if (name == null || name.isEmpty()) name = "User Not Found";
        
        tvName.setText(name);
        tvEmail.setText(userEmail);
        if (tvRole != null) tvRole.setText(role.toUpperCase());
        
        if ("student".equalsIgnoreCase(role)) {
            if (llStudentStats != null) llStudentStats.setVisibility(View.VISIBLE);
            if (cvQRCode != null) cvQRCode.setVisibility(View.VISIBLE);
            if (tvDigitalTitle != null) tvDigitalTitle.setVisibility(View.VISIBLE);
            
            String studentId = db.getStudentId(userEmail);
            if (tvId != null) tvId.setText(studentId);
            double fine = db.getTotalFine(userEmail);
            if (tvFine != null) tvFine.setText(String.format(Locale.getDefault(), "₹%.2f", fine));
            generateQRCode(studentId + "|" + name);
        } else {
            if (llStudentStats != null) llStudentStats.setVisibility(View.GONE);
            if (cvQRCode != null) cvQRCode.setVisibility(View.GONE);
            if (tvDigitalTitle != null) tvDigitalTitle.setVisibility(View.GONE);
        }
        
        loadProfileImage();
    }

    private void showEditNameDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_name, null);
        EditText etNewName = view.findViewById(R.id.etNewName);
        etNewName.setText(tvName.getText().toString());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Name")
                .setView(view)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newName = etNewName.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        if (db.updateUserName(userEmail, newName)) {
                            tvName.setText(newName);
                            if (isViewingOwnProfile) {
                                sharedPreferences.edit().putString("name", newName).apply();
                            }
                            Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Failed to update name", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLogoutConfirm() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    sharedPreferences.edit().clear().apply();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Stay", null)
                .show();
    }

    private void generateQRCode(String text) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 400, 400);
            ivQRCode.setImageBitmap(new BarcodeEncoder().createBitmap(matrix));
        } catch (WriterException e) { e.printStackTrace(); }
    }

    private void loadProfileImage() {
        byte[] img = db.getUserProfileImage(userEmail);
        if (img != null) {
            ivProfileImage.setImageBitmap(BitmapFactory.decodeByteArray(img, 0, img.length));
        } else {
            ivProfileImage.setImageResource(R.drawable.ic_profile);
        }
    }

    private byte[] getBytesFromBitmap(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream);
        return stream.toByteArray();
    }
}
