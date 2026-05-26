package com.example.librarymanagement;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class ManageStudentsActivity extends AppCompatActivity {

    RecyclerView studentsRecycler;
    Button btnAddStudent;
    SearchView searchView;
    MaterialToolbar toolbar;

    ArrayList<Student> studentList;
    StudentAdapter adapter;
    DatabaseHelper db;
    FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_students);

        studentsRecycler = findViewById(R.id.studentsRecycler);
        btnAddStudent = findViewById(R.id.btnAddStudent);
        searchView = findViewById(R.id.searchView);
        toolbar = findViewById(R.id.toolbar);
        
        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        btnAddStudent.setVisibility(View.GONE);

        studentList = new ArrayList<>();
        adapter = new StudentAdapter(this, studentList);
        studentsRecycler.setLayoutManager(new LinearLayoutManager(this));
        studentsRecycler.setAdapter(adapter);

        listenForStudentsLive();

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
    }

    private void listenForStudentsLive() {
        firestore.collection("users")
                .whereEqualTo("role", "student")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            loadStudentsOffline();
                            return;
                        }

                        if (value != null) {
                            ArrayList<Student> newList = new ArrayList<>();
                            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
                            
                            for (DocumentSnapshot doc : value.getDocuments()) {
                                String email = doc.getString("email");
                                // Fetch local ID to ensure sync with SQLite
                                int localId = db.getUserIdByEmail(email);
                                
                                Student s = new Student(
                                        localId, 
                                        doc.getString("name"),
                                        email,
                                        doc.getString("student_id")
                                );
                                s.setStatus(doc.getString("status"));
                                
                                Timestamp ts = doc.getTimestamp("lastSeen");
                                if (ts != null) {
                                    s.setLastSeen(sdf.format(ts.toDate()));
                                }
                                
                                Long timeSpent = doc.getLong("totalTimeSpent");
                                if (timeSpent != null) {
                                    s.setTimeSpent(timeSpent);
                                }

                                newList.add(s);
                            }
                            adapter.updateList(newList);
                        }
                    }
                });
    }

    private void loadStudentsOffline() {
        ArrayList<Student> offlineList = new ArrayList<>();
        SQLiteDatabase database = db.getReadableDatabase();
        Cursor cursor = database.rawQuery("SELECT id, name, email, student_id FROM users WHERE role='student'", null);
        if (cursor.moveToFirst()) {
            do {
                offlineList.add(new Student(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        adapter.updateList(offlineList);
    }
}
