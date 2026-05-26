package com.example.librarymanagement;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.ViewHolder> {

    Context context;
    List<Request> list;
    DatabaseHelper db;
    FirebaseFirestore firestore;

    public RequestAdapter(Context context, List<Request> list) {
        this.context = context;
        this.list = list;
        this.db = new DatabaseHelper(context);
        this.firestore = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Request request = list.get(position);
        holder.tvBook.setText(request.getBookName());
        holder.tvAuthor.setText(request.getAuthorName());
        holder.tvEmail.setText(request.getStudentEmail());
        holder.tvStatus.setText("Status: " + request.getStatus());
        holder.tvDate.setText(request.getDate());

        if ("Pending".equalsIgnoreCase(request.getStatus())) {
            holder.layoutActions.setVisibility(View.VISIBLE);
        } else {
            holder.layoutActions.setVisibility(View.GONE);
        }

        holder.btnApprove.setOnClickListener(v -> {
            checkAndApprove(request, holder.getAdapterPosition());
        });

        holder.btnReject.setOnClickListener(v -> {
            updateRequestStatus(request.getId(), "Rejected", holder.getAdapterPosition());
        });
    }

    private void checkAndApprove(Request request, int position) {
        // 1. Sync Student first if missing
        if (!db.checkUserExists(request.getStudentEmail())) {
            firestore.collection("users").document(request.getStudentEmail()).get()
                    .addOnSuccessListener(userDoc -> {
                        if (userDoc.exists()) {
                            db.insertUserWithId(
                                    userDoc.getString("name"),
                                    userDoc.getString("email"),
                                    "123456", // Temporary pass for sync
                                    "student",
                                    userDoc.getString("student_id")
                            );
                            syncBookAndApprove(request, position);
                        } else {
                            // If doc ID is not email, try query
                            firestore.collection("users")
                                    .whereEqualTo("email", request.getStudentEmail())
                                    .get()
                                    .addOnSuccessListener(querySnapshot -> {
                                        if (!querySnapshot.isEmpty()) {
                                            DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                                            db.insertUserWithId(
                                                    doc.getString("name"),
                                                    doc.getString("email"),
                                                    "123456",
                                                    "student",
                                                    doc.getString("student_id")
                                            );
                                            syncBookAndApprove(request, position);
                                        } else {
                                            Toast.makeText(context, "Error: Student not registered in Cloud", Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }
                    });
        } else {
            syncBookAndApprove(request, position);
        }
    }

    private void syncBookAndApprove(Request request, int position) {
        if (db.getBookIdByAccessionFromTitle(request.getBookName()) == -1) {
            firestore.collection("books")
                    .whereEqualTo("title", request.getBookName())
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                            int qty = 1;
                            if (doc.contains("quantity")) qty = doc.getLong("quantity").intValue();
                            else if (doc.contains("available_quantity")) qty = doc.getLong("available_quantity").intValue();

                            db.insertBookFull(
                                    doc.getString("title"), doc.getString("author"), doc.getString("category"),
                                    null, doc.getString("accession_no"), doc.getString("publisher"),
                                    doc.getString("edition"), doc.getString("year"), doc.getString("pages"),
                                    doc.getString("purchase_date"), doc.getString("mrp"), doc.getString("purchase_price"),
                                    doc.getString("discount"), qty, null
                            );
                            approveRequest(request, position);
                        } else {
                            Toast.makeText(context, "Error: Book not found in Library Records", Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            approveRequest(request, position);
        }
    }

    private void approveRequest(Request request, int position) {
        int bookId = db.getBookIdByAccessionFromTitle(request.getBookName());
        int userId = db.getUserIdByEmail(request.getStudentEmail());
        
        Book bookObj = db.getBookById(bookId);

        if (bookId != -1 && userId != -1 && bookObj != null) {
            int quantity = db.getBookQuantity(bookId);
            if (quantity <= 0) {
                Toast.makeText(context, "Error: Book Out of Stock", Toast.LENGTH_SHORT).show();
                return;
            }

            int newQty = quantity - 1;
            String accessionNo = bookObj.getAccessionNo();

            // 1. Update Firestore Request Status
            updateRequestStatus(request.getId(), "Approved", position);
            
            // 2. Global Stock Update
            firestore.collection("books").document(accessionNo).update("quantity", newQty);
            
            // 3. Create Loan Record in Firestore for Student Dashboard
            Map<String, Object> loan = new HashMap<>();
            loan.put("book_accession", accessionNo);
            loan.put("book_title", bookObj.getTitle());
            loan.put("student_id", db.getStudentId(request.getStudentEmail()));
            loan.put("student_email", request.getStudentEmail());
            loan.put("date", request.getDate());
            loan.put("status", "Issued");
            loan.put("issued_by", "Admin (Request)");

            firestore.collection("loans").add(loan);
            
            // 4. Local Updates
            db.updateBookQuantity(bookId, -1);
            db.insertLoan(bookId, userId, request.getDate(), "Issued");

            Toast.makeText(context, "Request Approved & Book Issued!", Toast.LENGTH_SHORT).show();
        } else {
            Log.e("ApproveRequest", "IDs not found. BookID: " + bookId + ", UserID: " + userId);
            Toast.makeText(context, "Approval Failed: System Sync Issue", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRequestStatus(String requestId, String status, int position) {
        if (requestId == null) {
            // If ID is missing, we try to find it by email and book name
            firestore.collection("requests")
                    .whereEqualTo("studentEmail", list.get(position).getStudentEmail())
                    .whereEqualTo("bookName", list.get(position).getBookName())
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            String actualId = queryDocumentSnapshots.getDocuments().get(0).getId();
                            firestore.collection("requests").document(actualId).update("status", status);
                        }
                    });
            return;
        }
        firestore.collection("requests").document(requestId).update("status", status);
        if (position != RecyclerView.NO_POSITION && position < list.size()) {
            list.get(position).setStatus(status);
            notifyItemChanged(position);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBook, tvAuthor, tvEmail, tvStatus, tvDate;
        Button btnApprove, btnReject;
        LinearLayout layoutActions;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBook = itemView.findViewById(R.id.tvReqBookName);
            tvAuthor = itemView.findViewById(R.id.tvReqAuthorName);
            tvEmail = itemView.findViewById(R.id.tvReqStudentEmail);
            tvStatus = itemView.findViewById(R.id.tvReqStatus);
            tvDate = itemView.findViewById(R.id.tvReqDate);
            btnApprove = itemView.findViewById(R.id.btnApprove);
            btnReject = itemView.findViewById(R.id.btnReject);
            layoutActions = itemView.findViewById(R.id.layoutActions);
        }
    }
}
