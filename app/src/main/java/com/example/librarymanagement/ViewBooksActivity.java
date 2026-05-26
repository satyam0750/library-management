package com.example.librarymanagement;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ViewBooksActivity extends AppCompatActivity {

    RecyclerView recyclerBooks;
    Button btnAddBook;
    SearchView searchView;
    DatabaseHelper db;
    ArrayList<Book> bookList;
    BookAdapter adapter;
    FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_books);

        recyclerBooks = findViewById(R.id.recyclerBooks);
        btnAddBook = findViewById(R.id.btnAddBook);
        searchView = findViewById(R.id.searchView);

        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();
        bookList = new ArrayList<>();

        recyclerBooks.setLayoutManager(new LinearLayoutManager(this));

        loadBooks();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (adapter != null) {
                    adapter.getFilter().filter(newText);
                }
                return true;
            }
        });

        String role = getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("role", "student");
        if (role != null && role.equals("student")) {
            btnAddBook.setVisibility(View.GONE);
        }

        btnAddBook.setOnClickListener(v ->
                startActivity(new Intent(this, AddBookActivity.class)));
    }

    private void loadBooks() {
        // Show offline data first
        refreshUI();

        firestore.collection("books")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            try {
                                String accession = doc.getString("accession_no");
                                if (accession == null) continue;

                                String title = doc.getString("title");
                                String author = doc.getString("author");
                                String category = doc.getString("category");
                                String publisher = doc.getString("publisher");
                                String edition = doc.getString("edition");
                                String year = doc.getString("year");
                                String pages = doc.getString("pages");
                                String date = doc.getString("purchase_date");
                                String mrp = doc.getString("mrp");
                                String pPrice = doc.getString("purchase_price");
                                String discount = doc.getString("discount");
                                
                                // Crucial: sync quantity correctly
                                int qty = 1;
                                if (doc.contains("quantity")) {
                                    qty = doc.getLong("quantity").intValue();
                                } else if (doc.contains("available_quantity")) {
                                    qty = doc.getLong("available_quantity").intValue();
                                }

                                // Always use insertBookFull - it uses CONFLICT_REPLACE and will update quantity
                                db.insertBookFull(title, author, category, null, accession, 
                                        publisher, edition, year, pages, date, mrp, pPrice, discount, qty, null);
                                
                            } catch (Exception e) {
                                Log.e("ViewBooks", "Error syncing book doc", e);
                            }
                        }
                        refreshUI();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Cloud sync failed. Showing local data.", Toast.LENGTH_SHORT).show();
                });
    }

    private void refreshUI() {
        bookList.clear();
        bookList.addAll(db.getAllBooks());
        if (adapter == null) {
            adapter = new BookAdapter(this, bookList);
            recyclerBooks.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBooks();
        searchView.setQuery("", false);
        searchView.clearFocus();
    }
}
