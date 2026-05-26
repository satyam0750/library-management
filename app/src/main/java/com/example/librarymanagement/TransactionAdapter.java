package com.example.librarymanagement;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder>{

    Context context;
    List<Transaction> list;
    DatabaseHelper db;
    String userRole, currentUserEmail;
    FirebaseFirestore firestore;
    private String dbUrl = "https://library-management-5e49a-default-rtdb.asia-southeast1.firebasedatabase.app";

    public TransactionAdapter(Context context,List<Transaction> list){
        this.context = context;
        this.list = list;
        this.db = new DatabaseHelper(context);
        this.firestore = FirebaseFirestore.getInstance();
        
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        this.userRole = prefs.getString("role", "student");
        this.currentUserEmail = prefs.getString("email", "Unknown");
    }

    public class ViewHolder extends RecyclerView.ViewHolder{

        TextView tvMember,tvBook,tvType,tvDate;
        Button btnReturn;

        public ViewHolder(View itemView){
            super(itemView);

            tvMember = itemView.findViewById(R.id.tvMember);
            tvBook = itemView.findViewById(R.id.tvBook);
            tvType = itemView.findViewById(R.id.tvType);
            tvDate = itemView.findViewById(R.id.tvDate);
            btnReturn = itemView.findViewById(R.id.btnReturnBook);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent,int viewType){
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_transaction,parent,false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder,int position){
        Transaction t = list.get(position);

        holder.tvMember.setText("Member: " + t.getMember());
        holder.tvBook.setText(t.getBook());
        
        if ("Returned".equalsIgnoreCase(t.getType())) {
            holder.tvType.setText("Status: Returned");
            holder.tvType.setTextColor(Color.parseColor("#4CAF50"));
            
            String dateInfo = "Issued: " + t.getDate();
            if (t.getReturnDate() != null && !t.getReturnDate().isEmpty()) dateInfo += " | Ret: " + t.getReturnDate();
            if (t.getFineAmount() > 0) dateInfo += " | Fine: ₹" + t.getFineAmount();
            
            holder.tvDate.setText(dateInfo);
            holder.btnReturn.setVisibility(View.GONE);
        } else {
            holder.tvType.setText("Status: Issued");
            holder.tvType.setTextColor(Color.parseColor("#FF9800"));
            holder.tvDate.setText("Issued: " + t.getDate());

            if (isStaffRole(userRole)) {
                holder.btnReturn.setVisibility(View.VISIBLE);
                holder.btnReturn.setOnClickListener(v -> {
                    processBookReturn(t, holder.getAdapterPosition());
                });
            } else {
                holder.btnReturn.setVisibility(View.GONE);
            }
        }
    }

    private boolean isStaffRole(String role) {
        return "admin".equalsIgnoreCase(role) || "librarian".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role);
    }

    private void processBookReturn(Transaction t, int position) {
        String studentEmail = t.getStudentEmail();
        String bookAccession = t.getBookAccession();
        
        // Data Recovery if missing
        if (studentEmail == null || bookAccession == null) {
            SQLiteDatabase rdb = db.getReadableDatabase();
            android.database.Cursor cursor = rdb.rawQuery("SELECT u.email, b.accession_no FROM loans l " +
                    "JOIN books b ON l.book_id = b.id " +
                    "JOIN users u ON l.member_id = u.id " +
                    "WHERE l.id = ?", new String[]{String.valueOf(t.getId())});
            if (cursor.moveToFirst()) {
                studentEmail = cursor.getString(0);
                bookAccession = cursor.getString(1);
                t.setStudentEmail(studentEmail);
                t.setBookAccession(bookAccession);
            }
            cursor.close();
        }

        if (studentEmail == null || bookAccession == null) {
            Toast.makeText(context, "Error: Could not identify student or book for return", Toast.LENGTH_LONG).show();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        double fineValue = calculateFine(t.getDate());
        
        final String finalEmail = studentEmail;
        final String finalAccession = bookAccession;
        final double finalFine = fineValue;

        // 1. Update Cloud (Primary)
        firestore.collection("loans")
                .whereEqualTo("student_email", finalEmail)
                .whereEqualTo("book_accession", finalAccession)
                .whereEqualTo("status", "Issued")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        WriteBatch batch = firestore.batch();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                            batch.update(doc.getReference(), "status", "Returned", "return_date", currentDate, "fine", finalFine);
                        }
                        
                        batch.commit().addOnSuccessListener(aVoid -> {
                            // Update Cloud Quantity
                            updateCloudQuantity(finalAccession);
                            
                            // 2. Update Local SQLite
                            boolean localUpdated = db.returnBook(t.getId(), finalEmail, finalAccession, currentDate, finalFine);
                            
                            // 3. UI Update
                            db.logActivity(currentUserEmail, "Return Book", "Book: " + finalAccession + " by " + finalEmail);
                            t.setType("Returned");
                            t.setReturnDate(currentDate);
                            t.setFineAmount(finalFine);
                            notifyItemChanged(position);
                            Toast.makeText(context, "Book Returned and Synced Successfully", Toast.LENGTH_SHORT).show();
                        }).addOnFailureListener(e -> {
                            Toast.makeText(context, "Firestore Update Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        // Fallback: If not found in Firestore, maybe it was issued locally only or has a different record
                        boolean localUpdated = db.returnBook(t.getId(), finalEmail, finalAccession, currentDate, finalFine);
                        if (localUpdated) {
                            t.setType("Returned");
                            t.setReturnDate(currentDate);
                            t.setFineAmount(finalFine);
                            notifyItemChanged(position);
                            Toast.makeText(context, "Returned locally (Record not found in cloud)", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Error: Loan record not found in Cloud or Local DB", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Network Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private double calculateFine(String issueDateStr) {
        if (issueDateStr == null || issueDateStr.isEmpty()) return 0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date issueDate = sdf.parse(issueDateStr);
            Date today = new Date();
            if (issueDate != null) {
                long diff = Math.abs(today.getTime() - issueDate.getTime());
                long days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
                if (days > 7) return (days - 7) * 5;
            }
        } catch (Exception e) { Log.e("FineCalc", e.getMessage()); }
        return 0;
    }

    private void updateCloudQuantity(String accession) {
        firestore.collection("books").document(accession).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                Long q = doc.getLong("quantity");
                long newQty = (q != null ? q : 0) + 1;
                firestore.collection("books").document(accession).update("quantity", newQty);
                FirebaseDatabase.getInstance(dbUrl).getReference("books").child(accession).child("quantity").setValue(newQty);
            }
        });
    }

    @Override
    public int getItemCount(){
        return list.size();
    }
}
