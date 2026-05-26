package com.example.librarymanagement;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BookDetailsActivity extends AppCompatActivity {

    private ImageView ivCover;
    private TextView tvTitle, tvAuthor, tvAccession, tvPublisher, tvEdition, tvYear, tvPages, tvPrice, tvQuantity;
    private Chip chipCategory;
    private MaterialButton btnRequest, btnReadPdf;
    private DatabaseHelper db;
    private FirebaseFirestore firestore;
    private SharedPreferences sharedPreferences;
    private String userRole, userEmail;
    private int currentQuantity = 0;
    private int bookId = -1;
    private String accessionNo = null;
    private String pdfPath = null;
    private boolean isBookIssuedToMe = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        userRole = sharedPreferences.getString("role", "student");
        userEmail = sharedPreferences.getString("email", "");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_details);

        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ivCover = findViewById(R.id.ivBookDetailsCover);
        tvTitle = findViewById(R.id.tvDetailsTitle);
        tvAuthor = findViewById(R.id.tvDetailsAuthor);
        tvAccession = findViewById(R.id.tvDetailsAccession);
        tvPublisher = findViewById(R.id.tvDetailsPublisher);
        tvEdition = findViewById(R.id.tvDetailsEdition);
        tvYear = findViewById(R.id.tvDetailsYear);
        tvPages = findViewById(R.id.tvDetailsPages);
        tvPrice = findViewById(R.id.tvDetailsPrice);
        tvQuantity = findViewById(R.id.tvDetailsQuantity);
        chipCategory = findViewById(R.id.chipCategory);
        btnRequest = findViewById(R.id.btnRequestBook);
        btnReadPdf = findViewById(R.id.btnReadPdf);

        bookId = getIntent().getIntExtra("BOOK_ID", -1);
        accessionNo = getIntent().getStringExtra("ACCESSION");

        if (bookId != -1 && bookId != 0) {
            loadBookFromLocal(bookId);
        } else if (accessionNo != null) {
            loadBookFromFirestore(accessionNo);
        } else {
            Toast.makeText(this, "Error: Book identity missing", Toast.LENGTH_SHORT).show();
            finish();
        }

        btnRequest.setOnClickListener(v -> {
            if (currentQuantity <= 0) {
                Toast.makeText(this, "Book is currently Out of Stock!", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String title = tvTitle.getText().toString();
            String author = tvAuthor.getText().toString().replace("by ", "");
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            String requestId = String.valueOf(System.currentTimeMillis());
            Map<String, Object> reqMap = new HashMap<>();
            reqMap.put("id", requestId);
            reqMap.put("bookName", title);
            reqMap.put("authorName", author);
            reqMap.put("studentEmail", userEmail);
            reqMap.put("status", "Pending");
            reqMap.put("date", date);

            firestore.collection("requests").document(requestId).set(reqMap)
                    .addOnSuccessListener(aVoid -> {
                        db.insertBookRequest(title, author, userEmail, date);
                        Toast.makeText(this, "Book Requested Successfully!", Toast.LENGTH_SHORT).show();
                        btnRequest.setEnabled(false);
                        btnRequest.setText("Request Pending");
                        updateButtons();
                    });
        });

        btnReadPdf.setOnClickListener(v -> {
            if (pdfPath != null && !pdfPath.isEmpty() && !pdfPath.equalsIgnoreCase("null")) {
                Intent intent = new Intent(this, PdfViewActivity.class);
                intent.putExtra("PDF_PATH", pdfPath);
                startActivity(intent);
            } else {
                Toast.makeText(this, "PDF not available for this book", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadBookFromLocal(int id) {
        Book book = db.getBookById(id);
        if (book != null) {
            pdfPath = book.getPdfPath();
            isBookIssuedToMe = db.isBookIssuedToUser(userEmail, id);
            
            // Debugging log
            Log.d("PDF_DEBUG", "Local PDF Path: " + pdfPath);
            
            // Sync fallback if local is empty
            if (pdfPath == null || pdfPath.isEmpty() || pdfPath.equalsIgnoreCase("null")) {
                firestore.collection("books").document(book.getAccessionNo()).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists() && doc.contains("book_pdf_path")) {
                                pdfPath = doc.getString("book_pdf_path");
                                Log.d("PDF_DEBUG", "Firestore Fallback Path: " + pdfPath);
                                updateButtons();
                            }
                        });
            }

            displayBookData(book.getTitle(), book.getAuthor(), book.getCategory(), book.getAccessionNo(),
                    book.getPublisher(), book.getEdition(), book.getYearOfPublication(),
                    book.getPages(), book.getMrpPrice(), book.getQuantity(), book.getCoverImage());
        } else if (accessionNo != null) {
            loadBookFromFirestore(accessionNo);
        }
    }

    private void loadBookFromFirestore(String accNo) {
        firestore.collection("books").document(accNo).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        int qty = doc.contains("quantity") ? doc.getLong("quantity").intValue() : 0;
                        pdfPath = doc.getString("book_pdf_path");
                        Log.d("PDF_DEBUG", "Firestore PDF Path: " + pdfPath);
                        
                        // Check issue status from Firestore if local check fails
                        int localId = db.getBookIdByAccession(accNo);
                        if (localId != -1) {
                            isBookIssuedToMe = db.isBookIssuedToUser(userEmail, localId);
                        }
                        
                        displayBookData(
                                doc.getString("title"),
                                doc.getString("author"),
                                doc.getString("category"),
                                doc.getString("accession_no"),
                                doc.getString("publisher"),
                                doc.getString("edition"),
                                doc.getString("year"),
                                doc.getString("pages"),
                                doc.getString("mrp"),
                                qty,
                                null
                        );
                    } else {
                        Toast.makeText(this, "Book details not found in cloud", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void displayBookData(String title, String author, String cat, String acc, String pub, String ed, String yr, String pg, String pr, int qty, byte[] img) {
        tvTitle.setText(title != null ? title : "N/A");
        tvAuthor.setText(author != null ? "by " + author : "by Unknown");
        chipCategory.setText(cat != null ? cat : "Other");
        tvAccession.setText(acc != null ? acc : "N/A");
        tvPublisher.setText(pub != null ? pub : "N/A");
        tvEdition.setText(ed != null ? ed : "N/A");
        tvYear.setText(yr != null ? yr : "N/A");
        tvPages.setText(pg != null ? pg : "N/A");
        tvPrice.setText(pr != null ? "₹" + pr : "₹0");
        
        currentQuantity = qty;
        tvQuantity.setText(String.valueOf(qty));

        if (img != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(img, 0, img.length);
            ivCover.setImageBitmap(bitmap);
        } else if (title != null) {
            // Auto-fetch high-res cover from OpenLibrary
            String coverUrl = "https://covers.openlibrary.org/b/title/" + title.replace(" ", "%20") + "-L.jpg";
            Glide.with(this)
                    .load(coverUrl)
                    .placeholder(R.drawable.book1)
                    .error(R.drawable.book1)
                    .into(ivCover);
        } else {
            ivCover.setImageResource(R.drawable.book1);
        }

        updateButtons();
        
        if (!"admin".equalsIgnoreCase(userRole) && title != null) {
            syncRequestStatus(userEmail, title);
        }
    }

    private void syncRequestStatus(String email, String title) {
        if (email == null || title == null || email.isEmpty()) return;
        
        firestore.collection("requests")
                .whereEqualTo("studentEmail", email)
                .whereEqualTo("bookName", title)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Find the most recent request if multiple exist
                        DocumentSnapshot latestReq = queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
                        String cloudStatus = latestReq.getString("status");
                        if (cloudStatus != null) {
                            db.updateRequestStatus(email, title, cloudStatus);
                            updateButtons(); // Refresh UI after sync
                        }
                    }
                });
    }

    private void updateButtons() {
        if ("admin".equalsIgnoreCase(userRole)) {
            btnRequest.setVisibility(View.GONE);
        } else {
            String bookTitle = tvTitle.getText().toString();
            boolean hasPending = db.hasPendingRequest(userEmail, bookTitle);
            
            if (isBookIssuedToMe) {
                btnRequest.setVisibility(View.GONE); // Hide request button if already issued
            } else if (hasPending) {
                btnRequest.setVisibility(View.VISIBLE);
                btnRequest.setEnabled(false);
                btnRequest.setText("Request Pending");
            } else {
                btnRequest.setVisibility(View.VISIBLE);
                if (currentQuantity <= 0) {
                    btnRequest.setEnabled(false);
                    btnRequest.setText("Out of Stock");
                } else {
                    btnRequest.setEnabled(true);
                    btnRequest.setText("Request This Book");
                }
            }
        }
        
        // Final visibility check for PDF Button: Show only if PDF exists AND (User is Admin OR Book is Issued/Approved)
        if (pdfPath != null && !pdfPath.isEmpty() && !pdfPath.equalsIgnoreCase("null")) {
            if ("admin".equalsIgnoreCase(userRole) || isBookIssuedToMe) {
                btnReadPdf.setVisibility(View.VISIBLE);
            } else {
                btnReadPdf.setVisibility(View.GONE);
            }
        } else {
            btnReadPdf.setVisibility(View.GONE);
        }
    }
}
