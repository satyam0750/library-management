package com.example.librarymanagement;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ManageStaffActivity extends AppCompatActivity {

    private RecyclerView recyclerStaff;
    private FloatingActionButton fabAdd;
    private Button btnViewLogs;
    private DatabaseHelper db;
    private StaffAdapter adapter;
    private List<Staff> staffList;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_staff);

        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();
        staffList = new ArrayList<>();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerStaff = findViewById(R.id.recyclerStaff);
        fabAdd = findViewById(R.id.fabAddStaff);
        btnViewLogs = findViewById(R.id.btnViewLogs);

        recyclerStaff.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StaffAdapter(this, staffList);
        recyclerStaff.setAdapter(adapter);
        
        listenForStaffLive();

        fabAdd.setOnClickListener(v -> {
            startActivity(new Intent(this, AddStaffActivity.class));
        });

        btnViewLogs.setOnClickListener(v -> {
            startActivity(new Intent(this, StaffLogsActivity.class));
        });
    }

    private void listenForStaffLive() {
        firestore.collection("users")
                .whereIn("role", java.util.Arrays.asList("librarian", "assistant"))
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            return;
                        }

                        if (value != null) {
                            staffList.clear();
                            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
                            
                            for (DocumentSnapshot doc : value.getDocuments()) {
                                String email = doc.getString("email");
                                // Fetch local ID to support Edit/Delete operations
                                int localId = db.getUserIdByEmail(email);
                                
                                Staff s = new Staff(
                                        localId,
                                        doc.getString("name"),
                                        email,
                                        doc.getString("role"),
                                        doc.getString("status"),
                                        doc.getString("assigned_tasks")
                                );
                                
                                // Get Last Seen
                                Timestamp ts = doc.getTimestamp("lastSeen");
                                if (ts != null) {
                                    s.setLastSeen(sdf.format(ts.toDate()));
                                }
                                
                                // Get Time Spent
                                Long timeSpent = doc.getLong("totalTimeSpent");
                                if (timeSpent != null) {
                                    s.setTotalTimeSpent(timeSpent);
                                }

                                staffList.add(s);
                            }
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
