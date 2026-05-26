package com.example.librarymanagement;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.librarymanagement.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class StaffLogsActivity extends AppCompatActivity {

    private RecyclerView recyclerLogs;
    private DatabaseHelper db;
    private List<StaffLog> logList;
    private StaffLogAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_logs);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerLogs = findViewById(R.id.recyclerLogs);
        db = new DatabaseHelper(this);
        logList = new ArrayList<>();

        recyclerLogs.setLayoutManager(new LinearLayoutManager(this));
        loadLogs();
    }

    private void loadLogs() {
        logList.clear();
        SQLiteDatabase database = db.getReadableDatabase();
        Cursor cursor = database.rawQuery("SELECT staff_email, action, target, timestamp FROM staff_logs ORDER BY id DESC", null);

        if (cursor.moveToFirst()) {
            do {
                logList.add(new StaffLog(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3)
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();

        adapter = new StaffLogAdapter(logList);
        recyclerLogs.setAdapter(adapter);
    }
}
