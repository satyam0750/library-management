package com.example.librarymanagement;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class IssueBookActivity extends AppCompatActivity {

    EditText etBookId, etMemberId, etDate;
    Button btnIssueBook;
    ImageButton btnScanBook, btnScanStudent;
    DatabaseHelper db;
    FirebaseFirestore firestore;
    DatabaseReference mDatabase;
    SharedPreferences sharedPreferences;
    String currentUserEmail;
    boolean isScanningBook = true;
    private String dbUrl = "https://library-management-5e49a-default-rtdb.asia-southeast1.firebasedatabase.app";

    private final ActivityResultLauncher<Intent> barcodeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String scannedValue = result.getData().getStringExtra("SCAN_RESULT");
                    if (isScanningBook) {
                        etBookId.setText(scannedValue);
                    } else {
                        etMemberId.setText(scannedValue);
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openScanner();
                } else {
                    Toast.makeText(this, "Camera permission is required to scan codes", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_issue_book);

        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();
        mDatabase = FirebaseDatabase.getInstance(dbUrl).getReference();

        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        currentUserEmail = sharedPreferences.getString("email", "Unknown");

        etBookId = findViewById(R.id.etBookId);
        etMemberId = findViewById(R.id.etMemberId);
        etDate = findViewById(R.id.etDate);
        btnIssueBook = findViewById(R.id.btnIssueBook);
        btnScanBook = findViewById(R.id.btnScanBook);
        btnScanStudent = findViewById(R.id.btnScanStudent);

        etDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> showDatePickerDialog());

        btnScanBook.setOnClickListener(v -> {
            isScanningBook = true;
            checkPermissionAndOpenScanner();
        });

        btnScanStudent.setOnClickListener(v -> {
            isScanningBook = false;
            checkPermissionAndOpenScanner();
        });

        btnIssueBook.setOnClickListener(v -> issueBookProcess());
    }

    private void showDatePickerDialog() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    String date = year1 + "-" + String.format(Locale.getDefault(), "%02d", (monthOfYear + 1)) + "-" + String.format(Locale.getDefault(), "%02d", dayOfMonth);
                    etDate.setText(date);
                }, year, month, day);
        datePickerDialog.show();
    }

    private void issueBookProcess() {
        String bookAccession = etBookId.getText().toString().trim();
        String studentId = etMemberId.getText().toString().trim();
        String date = etDate.getText().toString().trim();

        if (bookAccession.isEmpty() || studentId.isEmpty() || date.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        btnIssueBook.setEnabled(false);

        firestore.collection("users")
                .whereEqualTo("student_id", studentId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String studentEmail = queryDocumentSnapshots.getDocuments().get(0).getString("email");
                        String studentName = queryDocumentSnapshots.getDocuments().get(0).getString("name");
                        
                        if (!db.checkUserExists(studentEmail)) {
                            db.insertUserWithId(studentName, studentEmail, "123456", "student", studentId);
                        }
                        
                        proceedWithIssuing(bookAccession, studentId, studentEmail, studentName, date);
                    } else {
                        btnIssueBook.setEnabled(true);
                        Toast.makeText(this, "Student ID not found!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    btnIssueBook.setEnabled(true);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void proceedWithIssuing(String bookAccession, String studentId, String studentEmail, String studentName, String date) {
        int bookDbId = db.getBookIdByAccession(bookAccession);
        int studentDbId = db.getUserIdByStudentId(studentId);
        Book bookObj = db.getBookByAccession(bookAccession);

        if (bookDbId == -1 || bookObj == null) {
            btnIssueBook.setEnabled(true);
            Toast.makeText(this, "Book not found locally!", Toast.LENGTH_SHORT).show();
            return;
        }

        int currentQty = db.getBookQuantity(bookDbId);
        if (currentQty <= 0) {
            btnIssueBook.setEnabled(true);
            Toast.makeText(this, "Book Out of Stock!", Toast.LENGTH_SHORT).show();
            return;
        }

        int newQty = currentQty - 1;

        Map<String, Object> loan = new HashMap<>();
        loan.put("book_accession", bookAccession);
        loan.put("book_title", bookObj.getTitle());
        loan.put("student_id", studentId);
        loan.put("student_name", studentName);
        loan.put("student_email", studentEmail);
        loan.put("date", date);
        loan.put("status", "Issued");
        loan.put("issued_by", currentUserEmail);

        firestore.collection("books").document(bookAccession).update("quantity", newQty);
        firestore.collection("loans").add(loan)
                .addOnSuccessListener(documentReference -> {
                    db.updateBookQuantity(bookDbId, -1);
                    db.insertLoan(bookDbId, studentDbId, date, "Issued");
                    db.logActivity(currentUserEmail, "Issue Book", "Book: " + bookAccession + " to " + studentId);

                    mDatabase.child("books").child(bookAccession).child("quantity").setValue(newQty);
                    mDatabase.child("loans").push().setValue(loan);

                    Toast.makeText(this, "Book Issued Successfully!", Toast.LENGTH_SHORT).show();
                    etBookId.setText("");
                    etMemberId.setText("");
                    btnIssueBook.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    btnIssueBook.setEnabled(true);
                    Toast.makeText(this, "Sync Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void checkPermissionAndOpenScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openScanner();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openScanner() {
        Intent intent = new Intent(this, BarcodeScannerActivity.class);
        barcodeLauncher.launch(intent);
    }
}
