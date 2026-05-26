package com.example.librarymanagement;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.ViewHolder> implements Filterable {

    Context context;
    List<Student> list;
    List<Student> listFull;
    DatabaseHelper db;
    FirebaseFirestore firestore;
    String currentUserEmail;

    public StudentAdapter(Context context, List<Student> list) {
        this.context = context;
        this.list = list;
        this.listFull = new ArrayList<>(list);
        this.db = new DatabaseHelper(context);
        this.firestore = FirebaseFirestore.getInstance();
        
        SharedPreferences sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        this.currentUserEmail = sharedPreferences.getString("email", "Unknown");
    }

    public void updateList(List<Student> newList) {
        this.list = newList;
        this.listFull = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, email, studentId, tvStatus, tvLastSeen, tvTimeSpent;
        View ivStatusDot;
        ImageButton delete;

        public ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.studentName);
            email = itemView.findViewById(R.id.studentEmail);
            studentId = itemView.findViewById(R.id.tvStudentId);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            ivStatusDot = itemView.findViewById(R.id.ivStatusDot);
            delete = itemView.findViewById(R.id.btnDeleteStudent);
            tvLastSeen = itemView.findViewById(R.id.tvLastSeen);
            tvTimeSpent = itemView.findViewById(R.id.tvTimeSpent);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.student_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Student student = list.get(holder.getAdapterPosition());
        holder.name.setText(student.getName());
        holder.email.setText(student.getEmail());
        holder.studentId.setText("ID: " + student.getStudentId());

        String status = student.getStatus() != null ? student.getStatus() : "Offline";
        holder.tvStatus.setText(status);
        
        if ("Online".equalsIgnoreCase(status)) {
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50"));
            holder.ivStatusDot.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else {
            holder.tvStatus.setTextColor(Color.GRAY);
            holder.ivStatusDot.setBackgroundColor(Color.GRAY);
        }

        if (student.getLastSeen() != null && !student.getLastSeen().isEmpty()) {
            holder.tvLastSeen.setText("Last Seen: " + student.getLastSeen());
        } else {
            holder.tvLastSeen.setText("Last Seen: Never");
        }

        long minutes = student.getTimeSpent();
        if (minutes > 60) {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            holder.tvTimeSpent.setText("Total Time: " + hours + "h " + remainingMinutes + "m");
        } else {
            holder.tvTimeSpent.setText("Total Time: " + minutes + "m");
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ProfileActivity.class);
            intent.putExtra("EMAIL", student.getEmail());
            intent.putExtra("NAME", student.getName());
            context.startActivity(intent);
        });

        holder.delete.setOnClickListener(v -> {
            int adapterPos = holder.getAdapterPosition();
            if (adapterPos == RecyclerView.NO_POSITION) return;
            Student studentToDelete = list.get(adapterPos);

            new AlertDialog.Builder(context)
                .setTitle("Delete Student")
                .setMessage("Are you sure you want to delete student: " + studentToDelete.getName() + "?")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {
                    deleteStudent(studentToDelete, adapterPos);
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void deleteStudent(Student student, int position) {
        String email = student.getEmail();
        
        // 1. Delete from Firestore
        firestore.collection("users").document(email).delete()
            .addOnSuccessListener(aVoid -> {
                // 2. Delete from Local Database
                db.deleteUserByEmail(email);
                db.logActivity(currentUserEmail, "Delete Student", email);
                
                // 3. Update UI
                if (position < list.size()) {
                    list.remove(position);
                    listFull.remove(student);
                    notifyItemRemoved(position);
                    Toast.makeText(context, "Student removed successfully", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                Toast.makeText(context, "Error deleting from server: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    @Override
    public Filter getFilter() {
        return studentFilter;
    }

    private Filter studentFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Student> filteredList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(listFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (Student item : listFull) {
                    if (item.getName().toLowerCase().contains(filterPattern) ||
                        item.getStudentId().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            list.clear();
            list.addAll((List) results.values);
            notifyDataSetChanged();
        }
    };
}
