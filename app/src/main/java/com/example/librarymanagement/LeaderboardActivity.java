package com.example.librarymanagement;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    RecyclerView recyclerLeaderboard;
    LeaderboardAdapter adapter;
    DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerLeaderboard = findViewById(R.id.recyclerLeaderboard);
        db = new DatabaseHelper(this);

        setupLeaderboard();
    }

    private void setupLeaderboard() {
        // Database se real data lana
        List<StudentScore> scores = db.getLeaderboardData();

        if (scores.isEmpty()) {
            Toast.makeText(this, "No data available yet", Toast.LENGTH_SHORT).show();
        }

        adapter = new LeaderboardAdapter(this, scores);
        recyclerLeaderboard.setLayoutManager(new LinearLayoutManager(this));
        recyclerLeaderboard.setAdapter(adapter);
    }

    public static class StudentScore {
        public String name;
        public int booksRead;
        public int rank;

        public StudentScore(String name, int booksRead, int rank) {
            this.name = name;
            this.booksRead = booksRead;
            this.rank = rank;
        }
    }
}
