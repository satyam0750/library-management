package com.example.librarymanagement;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    RecyclerView recyclerHistory;
    DatabaseHelper db;
    List<Transaction> transactionList;
    TransactionAdapter adapter;
    View tvEmptyMessage;
    FirebaseFirestore firestore;
    String userRole, userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerHistory = findViewById(R.id.recyclerHistory);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        
        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();
        transactionList = new ArrayList<>();

        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        userRole = prefs.getString("role", "student");
        userEmail = prefs.getString("email", "");

        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(this, transactionList);
        recyclerHistory.setAdapter(adapter);

        loadHistory();
    }

    private boolean isStaff() {
        return "admin".equalsIgnoreCase(userRole) || "librarian".equalsIgnoreCase(userRole) || "assistant".equalsIgnoreCase(userRole);
    }

    private void loadHistory() {
        // Load local data first
        loadLocalHistory();
        // Sync with Firestore
        syncHistoryFromFirestore();
    }

    private void loadLocalHistory() {
        SQLiteDatabase database = db.getReadableDatabase();
        String query;
        String[] selectionArgs = null;

        // Fetching more columns: return_date, fine_amount, email, accession_no
        if (isStaff()) {
            query = "SELECT l.id, IFNULL(u.name, 'User'), IFNULL(b.title, 'Book'), l.status, l.date, l.return_date, l.fine_amount, u.email, b.accession_no " +
                    "FROM loans l " +
                    "LEFT JOIN books b ON l.book_id = b.id " +
                    "LEFT JOIN users u ON l.member_id = u.id " +
                    "ORDER BY l.id DESC";
        } else {
            query = "SELECT l.id, IFNULL(u.name, 'User'), IFNULL(b.title, 'Book'), l.status, l.date, l.return_date, l.fine_amount, u.email, b.accession_no " +
                    "FROM loans l " +
                    "LEFT JOIN books b ON l.book_id = b.id " +
                    "LEFT JOIN users u ON l.member_id = u.id " +
                    "WHERE u.email = ? " +
                    "ORDER BY l.id DESC";
            selectionArgs = new String[]{userEmail};
        }

        try {
            Cursor cursor = database.rawQuery(query, selectionArgs);
            transactionList.clear();
            if (cursor.moveToFirst()) {
                do {
                    Transaction t = new Transaction(
                            cursor.getInt(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.getString(4)
                    );
                    t.setReturnDate(cursor.getString(5));
                    t.setFineAmount(cursor.getDouble(6));
                    t.setStudentEmail(cursor.getString(7));
                    t.setBookAccession(cursor.getString(8));
                    transactionList.add(t);
                } while (cursor.moveToNext());
            }
            cursor.close();
            updateUI();
        } catch (Exception e) {
            Log.e("HistoryActivity", "Local DB Error: " + e.getMessage());
        }
    }

    private void syncHistoryFromFirestore() {
        Query firestoreQuery = firestore.collection("loans");
        if (!isStaff()) {
            firestoreQuery = firestoreQuery.whereEqualTo("student_email", userEmail);
        }

        firestoreQuery.get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                transactionList.clear();
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    String name = doc.getString("student_name");
                    if (name == null) name = doc.getString("student_id");
                    
                    Transaction t = new Transaction(
                            0, 
                            name != null ? name : "User",
                            doc.getString("book_title"),
                            doc.getString("status"),
                            doc.getString("date")
                    );
                    t.setReturnDate(doc.getString("return_date"));
                    Double fine = doc.getDouble("fine");
                    t.setFineAmount(fine != null ? fine : 0.0);
                    t.setStudentEmail(doc.getString("student_email"));
                    t.setBookAccession(doc.getString("book_accession"));
                    
                    // Try to get local ID if it exists
                    int localId = db.getLoanId(t.getStudentEmail(), t.getBookAccession());
                    if (localId != -1) {
                        t.setId(localId);
                    }
                    
                    transactionList.add(t);
                }
                // Sort descending by date
                Collections.sort(transactionList, (o1, o2) -> {
                    String d1 = o1.getDate() != null ? o1.getDate() : "";
                    String d2 = o2.getDate() != null ? o2.getDate() : "";
                    return d2.compareTo(d1);
                });
                updateUI();
            }
        }).addOnFailureListener(e -> Log.e("HistoryActivity", "Firestore sync failed"));
    }

    private void updateUI() {
        if (transactionList.isEmpty()) {
            recyclerHistory.setVisibility(View.GONE);
            if (tvEmptyMessage != null) tvEmptyMessage.setVisibility(View.VISIBLE);
        } else {
            recyclerHistory.setVisibility(View.VISIBLE);
            if (tvEmptyMessage != null) tvEmptyMessage.setVisibility(View.GONE);
        }
        adapter.notifyDataSetChanged();
    }
}
