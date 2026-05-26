package com.example.librarymanagement;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RequestBookActivity extends AppCompatActivity {

    TextInputEditText etBookName, etAuthorName;
    Button btnSubmitRequest;
    DatabaseHelper db;
    FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_book);

        etBookName = findViewById(R.id.etRequestBookName);
        etAuthorName = findViewById(R.id.etRequestAuthorName);
        btnSubmitRequest = findViewById(R.id.btnSubmitRequest);
        
        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();

        String studentEmail = getIntent().getStringExtra("EMAIL");
        if (studentEmail == null || studentEmail.isEmpty()) {
            studentEmail = getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("email", "");
        }

        final String finalEmail = studentEmail;

        btnSubmitRequest.setOnClickListener(v -> {
            String book = etBookName.getText().toString().trim();
            String author = etAuthorName.getText().toString().trim();
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            if (book.isEmpty() || author.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSubmitRequest.setEnabled(false);
            
            // Generate a random ID for the request
            String requestId = String.valueOf(System.currentTimeMillis());
            
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("id", requestId);
            requestMap.put("bookName", book);
            requestMap.put("authorName", author);
            requestMap.put("studentEmail", finalEmail);
            requestMap.put("status", "Pending");
            requestMap.put("date", date);

            firestore.collection("requests").document(requestId)
                    .set(requestMap)
                    .addOnSuccessListener(aVoid -> {
                        db.insertBookRequest(book, author, finalEmail, date);
                        Toast.makeText(this, "Request Sent Successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnSubmitRequest.setEnabled(true);
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }
}
