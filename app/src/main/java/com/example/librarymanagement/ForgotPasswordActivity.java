package com.example.librarymanagement;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Random;

public class ForgotPasswordActivity extends AppCompatActivity {

    EditText etEmail, etOTP, etNewPassword, etConfirmNewPassword;
    Button btnSendOTP, btnVerifyOTP, btnResetPassword;
    LinearLayout layoutOTP, layoutNewPassword;

    DatabaseHelper db;
    FirebaseAuth mAuth;
    String generatedOTP;
    String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        etEmail = findViewById(R.id.etForgotEmail);
        etOTP = findViewById(R.id.etOTP);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);

        btnSendOTP = findViewById(R.id.btnSendOTP);
        btnVerifyOTP = findViewById(R.id.btnVerifyOTP);
        btnResetPassword = findViewById(R.id.btnResetPassword);

        layoutOTP = findViewById(R.id.layoutOTP);
        layoutNewPassword = findViewById(R.id.layoutNewPassword);

        db = new DatabaseHelper(this);
        mAuth = FirebaseAuth.getInstance();

        btnSendOTP.setOnClickListener(v -> {
            userEmail = etEmail.getText().toString().trim();
            if (userEmail.isEmpty()) {
                Toast.makeText(this, "Please enter your registered email", Toast.LENGTH_SHORT).show();
                return;
            }

            if (db.checkUserExists(userEmail)) {
                // 1. Generate a random 4-digit OTP
                generatedOTP = String.valueOf(new Random().nextInt(9000) + 1000);
                
                // 2. Show OTP Input UI
                layoutOTP.setVisibility(View.VISIBLE);
                btnSendOTP.setVisibility(View.GONE);
                etEmail.setEnabled(false);

                // 3. Actual Email Sending:
                // Since Android doesn't allow automatic background email sending without a backend server,
                // we use a secure system Intent to send the OTP. This will open the user's Gmail/Outlook app.
                sendOTPViaEmailIntent(userEmail, generatedOTP);
                
                // For easier testing/debugging, we also show it in a Toast
                Toast.makeText(this, "OTP generated: " + generatedOTP, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "This email is not registered with us.", Toast.LENGTH_SHORT).show();
            }
        });

        btnVerifyOTP.setOnClickListener(v -> {
            String enteredOTP = etOTP.getText().toString().trim();
            if (enteredOTP.equals(generatedOTP)) {
                Toast.makeText(this, "OTP Verified Successfully", Toast.LENGTH_SHORT).show();
                layoutNewPassword.setVisibility(View.VISIBLE);
                layoutOTP.setVisibility(View.GONE);
            } else {
                Toast.makeText(this, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });

        btnResetPassword.setOnClickListener(v -> {
            String newPass = etNewPassword.getText().toString().trim();
            String confirmPass = etConfirmNewPassword.getText().toString().trim();

            if (newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(this, "Please enter new password", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPass.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirmPass)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            // Update password in local SQLite database
            if (db.updatePassword(userEmail, newPass)) {
                Toast.makeText(this, "Password Updated Successfully!", Toast.LENGTH_SHORT).show();
                finish(); // Go back to Login screen
            } else {
                Toast.makeText(this, "Failed to update password. Try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * This method triggers an Intent to send an email with the OTP.
     */
    private void sendOTPViaEmailIntent(String toEmail, String otp) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{toEmail});
        intent.putExtra(Intent.EXTRA_SUBJECT, "SmartLibrary - Password Reset OTP");
        intent.putExtra(Intent.EXTRA_TEXT, "Hello,\n\nYour OTP for resetting the password is: " + otp + 
                "\n\nPlease enter this code in the app to proceed with the password reset.\n\nThank you!");
        
        try {
            startActivity(Intent.createChooser(intent, "Send OTP via..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No email app found on this device.", Toast.LENGTH_SHORT).show();
        }
    }
}
