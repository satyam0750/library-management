package com.example.librarymanagement;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.ViewHolder> {

    private Context context;
    private List<Staff> staffList;
    private DatabaseHelper db;
    private FirebaseFirestore firestore;

    public StaffAdapter(Context context, List<Staff> staffList) {
        this.context = context;
        this.staffList = staffList;
        this.db = new DatabaseHelper(context);
        this.firestore = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_staff, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Staff staff = staffList.get(holder.getAdapterPosition());

        holder.tvName.setText(staff.getName());
        holder.tvRole.setText(staff.getRole());
        
        String status = staff.getStatus() != null ? staff.getStatus() : "Offline";
        holder.tvStatus.setText(status);
        if ("Online".equalsIgnoreCase(status)) {
            holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            holder.tvStatus.setTextColor(Color.GRAY);
        }

        if (staff.getLastSeen() != null && !staff.getLastSeen().isEmpty()) {
            holder.tvLastSeen.setText("Last Seen: " + staff.getLastSeen());
        } else {
            holder.tvLastSeen.setText("Last Seen: Never");
        }

        long minutes = staff.getTotalTimeSpent();
        if (minutes > 60) {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            holder.tvTimeSpent.setText("Total: " + hours + "h " + remainingMinutes + "m");
        } else {
            holder.tvTimeSpent.setText("Total: " + minutes + "m");
        }

        byte[] imageBytes = db.getUserProfileImage(staff.getEmail());
        if (imageBytes != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            holder.ivImage.setImageBitmap(bitmap);
        } else {
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_myplaces);
        }

        holder.btnDelete.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;
            
            Staff staffToDelete = staffList.get(currentPos);
            
            new AlertDialog.Builder(context)
                .setTitle("Delete Staff")
                .setMessage("Are you sure you want to delete staff member: " + staffToDelete.getName() + "?")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {
                    deleteStaffMember(staffToDelete, currentPos);
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        holder.btnEdit.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;
            Staff s = staffList.get(currentPos);
            
            Intent intent = new Intent(context, AddStaffActivity.class);
            intent.putExtra("EDIT_MODE", true);
            intent.putExtra("STAFF_ID", s.getId());
            intent.putExtra("NAME", s.getName());
            intent.putExtra("EMAIL", s.getEmail());
            intent.putExtra("ROLE", s.getRole());
            intent.putExtra("TASKS", s.getTasks());
            context.startActivity(intent);
        });
    }

    private void deleteStaffMember(Staff staff, int position) {
        String email = staff.getEmail();
        if (email == null || email.isEmpty()) {
            Toast.makeText(context, "Cannot delete: Email is missing", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Delete from Firestore first
        firestore.collection("users").document(email).delete()
            .addOnSuccessListener(aVoid -> {
                // 2. Delete from Local SQLite Database using Email
                boolean localDeleted = db.deleteUserByEmail(email);
                
                // 3. Update UI manually if listener doesn't trigger immediately
                if (position >= 0 && position < staffList.size()) {
                    staffList.remove(position);
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, staffList.size());
                }
                Toast.makeText(context, "Staff member deleted successfully", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(context, "Failed to delete from server: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    @Override
    public int getItemCount() {
        return staffList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvRole, tvStatus, tvLastSeen, tvTimeSpent;
        ShapeableImageView ivImage;
        ImageButton btnDelete, btnEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvStaffName);
            tvRole = itemView.findViewById(R.id.tvStaffRole);
            tvStatus = itemView.findViewById(R.id.tvStaffStatus);
            ivImage = itemView.findViewById(R.id.ivStaffImage);
            btnDelete = itemView.findViewById(R.id.btnDeleteStaff);
            btnEdit = itemView.findViewById(R.id.btnEditStaff);
            tvLastSeen = itemView.findViewById(R.id.tvStaffLastSeen);
            tvTimeSpent = itemView.findViewById(R.id.tvStaffTimeSpent);
        }
    }
}
