package com.example.librarymanagement;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class MyBooksActivity extends AppCompatActivity {

    RecyclerView recyclerMyBooks;
    Toolbar toolbar;
    DatabaseHelper db;
    ArrayList<Book> bookList;
    BookAdapter adapter;
    SharedPreferences sharedPreferences;
    FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_books);

        toolbar = findViewById(R.id.toolbar);
        recyclerMyBooks = findViewById(R.id.recyclerMyBooks);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("My Issued Books");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        
        recyclerMyBooks.setLayoutManager(new LinearLayoutManager(this));
        bookList = new ArrayList<>();
        adapter = new BookAdapter(this, bookList);
        recyclerMyBooks.setAdapter(adapter);

        loadMyBooksLive();
    }

    private void loadMyBooksLive() {
        String email = sharedPreferences.getString("email", "");
        if (email.isEmpty()) return;

        Log.d("MyBooks", "Loading books for email: " + email);

        // Step 1: First show what we have in local DB
        refreshList(email);

        // Step 2: Fetch latest from Firestore and Sync
        firestore.collection("loans")
                .whereEqualTo("student_email", email)
                .whereEqualTo("status", "Issued")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d("MyBooks", "Firestore found " + queryDocumentSnapshots.size() + " loans");
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            String accession = doc.getString("book_accession");
                            String loanDate = doc.getString("date");
                            syncBookToLocal(accession, email, loanDate);
                        }
                    } else {
                        Log.d("MyBooks", "No issued books found in Firestore for this user.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("MyBooks", "Firestore query failed", e);
                    Toast.makeText(this, "Sync failed. Showing offline books.", Toast.LENGTH_SHORT).show();
                });
    }

    private void syncBookToLocal(String accession, String email, String loanDate) {
        Log.d("MyBooks", "Syncing book: " + accession);
        firestore.collection("books").document(accession).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // 1. Ensure book exists in local DB
                        if (db.getBookIdByAccession(accession) == -1) {
                            Log.d("MyBooks", "Inserting book into local DB: " + accession);
                            db.insertBookFull(
                                    doc.getString("title"), doc.getString("author"), doc.getString("category"),
                                    null, accession, doc.getString("publisher"), doc.getString("edition"),
                                    doc.getString("year"), doc.getString("pages"), doc.getString("purchase_date"),
                                    doc.getString("mrp"), doc.getString("purchase_price"), doc.getString("discount"),
                                    1, null
                            );
                        }

                        // 2. Ensure loan record exists in local DB
                        int bookId = db.getBookIdByAccession(accession);
                        int userId = db.getUserIdByStudentId(db.getStudentId(email));
                        
                        if (bookId != -1 && userId != -1) {
                            // Check if loan already exists locally to avoid duplicates
                            if (!db.isBookIssuedToUser(email, bookId)) {
                                Log.d("MyBooks", "Inserting loan into local DB. BookID: " + bookId + ", UserID: " + userId);
                                db.insertLoan(bookId, userId, loanDate, "Issued");
                            }
                        } else {
                            Log.e("MyBooks", "Failed to get bookId or userId. BookID: " + bookId + ", UserID: " + userId);
                        }
                        
                        // 3. Refresh UI
                        refreshList(email);
                    } else {
                        Log.e("MyBooks", "Book document does not exist in Firestore: " + accession);
                    }
                })
                .addOnFailureListener(e -> Log.e("MyBooks", "Failed to fetch book details for sync", e));
    }

    private void refreshList(String email) {
        ArrayList<Book> updatedList = db.getIssuedBooksForStudent(email);
        Log.d("MyBooks", "Local DB has " + updatedList.size() + " issued books.");
        bookList.clear();
        bookList.addAll(updatedList);
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
