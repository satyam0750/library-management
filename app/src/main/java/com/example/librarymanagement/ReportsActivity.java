package com.example.librarymanagement;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportsActivity extends AppCompatActivity {

    TextView tvTotalBooks, tvTotalStudents, tvIssuedBooks, tvPendingRequests, tvTotalFines, tvReturnedToday;
    TextView tvActivityLog;
    MaterialCardView cardTotalBooks, cardTotalStudents, cardIssuedBooks, cardPendingRequests;
    MaterialButton btnExportPDF;
    DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        tvTotalBooks = findViewById(R.id.tvTotalBooks);
        tvTotalStudents = findViewById(R.id.tvTotalStudents);
        tvIssuedBooks = findViewById(R.id.tvIssuedBooks);
        tvPendingRequests = findViewById(R.id.tvPendingRequests);
        tvTotalFines = findViewById(R.id.tvTotalFines);
        tvReturnedToday = findViewById(R.id.tvReturnedToday);
        tvActivityLog = findViewById(R.id.tvActivityLog); 

        cardTotalBooks = findViewById(R.id.cardTotalBooks);
        cardTotalStudents = findViewById(R.id.cardTotalStudents);
        cardIssuedBooks = findViewById(R.id.cardIssuedBooks);
        cardPendingRequests = findViewById(R.id.cardPendingRequests);
        btnExportPDF = findViewById(R.id.btnExportStudentsPDF);

        db = new DatabaseHelper(this);

        loadReports();
        loadStudentActivityLog();
        setupClickListeners();
    }

    private void setupClickListeners() {
        cardTotalStudents.setOnClickListener(v -> {
            Intent intent = new Intent(this, ManageStudentsActivity.class);
            startActivity(intent);
        });

        cardTotalBooks.setOnClickListener(v -> {
            Intent intent = new Intent(this, ViewBooksActivity.class);
            startActivity(intent);
        });
        
        cardIssuedBooks.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
        });

        cardPendingRequests.setOnClickListener(v -> {
            Intent intent = new Intent(this, ManageRequestsActivity.class);
            startActivity(intent);
        });

        btnExportPDF.setOnClickListener(v -> exportStudentsToPDF());
    }

    private void exportStudentsToPDF() {
        List<Student> students = new ArrayList<>();
        SQLiteDatabase database = db.getReadableDatabase();
        Cursor cursor = database.rawQuery("SELECT id, name, email, student_id FROM users WHERE role='student'", null);
        if (cursor.moveToFirst()) {
            do {
                students.add(new Student(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)));
            } while (cursor.moveToNext());
        }
        cursor.close();

        if (students.isEmpty()) {
            Toast.makeText(this, "No students to export", Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint();
        Paint titlePaint = new Paint();

        // Page width 595 (A4 width in points)
        int pageWidth = 595;
        int pageHeight = 842;
        int pageNumber = 1;

        PdfDocument.PageInfo myPageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
        PdfDocument.Page myPage = pdfDocument.startPage(myPageInfo);
        Canvas canvas = myPage.getCanvas();

        // Title
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(20);
        titlePaint.setColor(Color.parseColor("#1F3C88"));
        canvas.drawText("Registered Students List", 20, 40, titlePaint);

        // Date
        paint.setTextSize(12);
        paint.setColor(Color.GRAY);
        String date = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText("Generated on: " + date, 20, 60, paint);

        // Table Header
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#EEEEEE"));
        canvas.drawRect(20, 80, pageWidth - 20, 110, paint);

        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(14);
        canvas.drawText("ID", 30, 100, paint);
        canvas.drawText("Name", 120, 100, paint);
        canvas.drawText("Email", 300, 100, paint);

        // Table Content
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        int y = 135;
        for (Student s : students) {
            if (y > pageHeight - 50) { // New page if limit reached
                pdfDocument.finishPage(myPage);
                pageNumber++;
                myPageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                myPage = pdfDocument.startPage(myPageInfo);
                canvas = myPage.getCanvas();
                y = 50;
            }
            canvas.drawText(s.getStudentId(), 30, y, paint);
            canvas.drawText(s.getName(), 120, y, paint);
            canvas.drawText(s.getEmail(), 300, y, paint);
            
            canvas.drawLine(20, y + 10, pageWidth - 20, y + 10, paint);
            y += 35;
        }

        pdfDocument.finishPage(myPage);

        String fileName = "Smart Library Students List - " + System.currentTimeMillis() + ".pdf";

        try {
            OutputStream outputStream;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                outputStream = getContentResolver().openOutputStream(uri);
            } else {
                java.io.File file = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                outputStream = new java.io.FileOutputStream(file);
            }

            pdfDocument.writeTo(outputStream);
            pdfDocument.close();
            outputStream.close();

            Toast.makeText(this, "PDF saved to Downloads", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadReports() {
        SQLiteDatabase database = db.getReadableDatabase();

        Cursor c1 = database.rawQuery("SELECT COUNT(*) FROM books", null);
        if (c1.moveToFirst()) tvTotalBooks.setText(String.valueOf(c1.getInt(0)));
        c1.close();

        Cursor c2 = database.rawQuery("SELECT COUNT(*) FROM users WHERE role='student'", null);
        if (c2.moveToFirst()) tvTotalStudents.setText(String.valueOf(c2.getInt(0)));
        c2.close();

        Cursor c3 = database.rawQuery("SELECT COUNT(*) FROM loans WHERE status='Issued'", null);
        if (c3.moveToFirst()) tvIssuedBooks.setText(String.valueOf(c3.getInt(0)));
        c3.close();

        Cursor c4 = database.rawQuery("SELECT COUNT(*) FROM requests WHERE status='Pending'", null);
        if (c4.moveToFirst()) tvPendingRequests.setText(String.valueOf(c4.getInt(0)));
        c4.close();

        Cursor c5 = database.rawQuery("SELECT SUM(fine_amount) FROM loans", null);
        if (c5.moveToFirst()) {
            double totalFine = c5.getDouble(0);
            tvTotalFines.setText(String.format(Locale.getDefault(), "₹ %.2f", totalFine));
        }
        c5.close();

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Cursor c6 = database.rawQuery("SELECT COUNT(*) FROM loans WHERE status='Returned' AND return_date=?", new String[]{today});
        if (c6.moveToFirst()) tvReturnedToday.setText(String.valueOf(c6.getInt(0)));
        c6.close();
    }

    private void loadStudentActivityLog() {
        if (tvActivityLog == null) return;

        SQLiteDatabase database = db.getReadableDatabase();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        String thirtyDaysAgo = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

        String query = "SELECT u.name, COUNT(l.id) as book_count " +
                       "FROM users u " +
                       "JOIN loans l ON u.id = l.member_id " +
                       "WHERE l.date >= ? " +
                       "GROUP BY u.id " +
                       "ORDER BY book_count DESC LIMIT 5";

        Cursor cursor = database.rawQuery(query, new String[]{thirtyDaysAgo});
        StringBuilder log = new StringBuilder("Top Readers (Last 30 Days):\n\n");
        
        if (cursor.moveToFirst()) {
            do {
                String name = cursor.getString(0);
                int count = cursor.getInt(1);
                log.append("• ").append(name).append(": ").append(count).append(" books\n");
            } while (cursor.moveToNext());
        } else {
            log.append("No activity recorded in the last 30 days.");
        }
        cursor.close();
        
        tvActivityLog.setText(log.toString());
    }
}
