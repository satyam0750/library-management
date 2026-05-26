package com.example.librarymanagement;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class ManageRequestsActivity extends AppCompatActivity {

    RecyclerView recyclerRequests;
    DatabaseHelper db;
    List<Request> requestList;
    RequestAdapter adapter;
    FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_requests);

        recyclerRequests = findViewById(R.id.recyclerRequests);
        db = new DatabaseHelper(this);
        requestList = new ArrayList<>();
        firestore = FirebaseFirestore.getInstance();

        recyclerRequests.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RequestAdapter(this, requestList);
        recyclerRequests.setAdapter(adapter);

        // LIVE Snapshot Listener for Firestore
        loadRequestsLive();
    }

    private void loadRequestsLive() {
        firestore.collection("requests")
                .orderBy("id", Query.Direction.DESCENDING)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            Toast.makeText(ManageRequestsActivity.this, "Sync Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (value != null) {
                            requestList.clear();
                            for (QueryDocumentSnapshot doc : value) {
                                Request req = new Request(
                                        doc.getString("id"),
                                        doc.getString("bookName"),
                                        doc.getString("authorName"),
                                        doc.getString("studentEmail"),
                                        doc.getString("status"),
                                        doc.getString("date")
                                );
                                requestList.add(req);
                            }
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
    }
}
