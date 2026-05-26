package com.example.librarymanagement;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StudentDashboardActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private BottomNavigationView bottomNav;
    private MaterialToolbar toolbar;
    private MaterialCardView cardBooks, cardRequest, cardIssued, cardProfile, cardLeaderboard;
    private TextView tvStudentName;
    private ImageView navProfileImage;
    private RecyclerView recyclerRecommendations;
    private RecommendationAdapter recommendationAdapter;
    private SharedPreferences sharedPreferences;
    private DatabaseHelper db;
    private FirebaseFirestore firestore;
    private String userEmail;
    private long sessionStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("dark_mode", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();
        userEmail = sharedPreferences.getString("email", "");
        sessionStartTime = System.currentTimeMillis();

        // Set status to Online
        setUserStatus("Online");

        // INIT UI
        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        bottomNav = findViewById(R.id.bottomNav);
        toolbar = findViewById(R.id.toolbar);
        tvStudentName = findViewById(R.id.tvStudentName);
        recyclerRecommendations = findViewById(R.id.recyclerRecommendations);
        
        cardBooks = findViewById(R.id.cardBooks);
        cardRequest = findViewById(R.id.cardRequest);
        cardIssued = findViewById(R.id.cardIssued);
        cardProfile = findViewById(R.id.cardProfile);
        cardLeaderboard = findViewById(R.id.cardLeaderboard);

        // Setup Toolbar
        setSupportActionBar(toolbar);

        // Sidebar Header Info
        View headerView = navView.getHeaderView(0);
        if (headerView != null) {
            TextView navName = headerView.findViewById(R.id.nav_name);
            TextView navEmail = headerView.findViewById(R.id.nav_email);
            navProfileImage = headerView.findViewById(R.id.nav_profile_image);
            
            String name = sharedPreferences.getString("name", "Student");
            if (navName != null) navName.setText(name);
            if (navEmail != null) navEmail.setText(userEmail);
            loadProfileImage(userEmail);
        }
        
        tvStudentName.setText(sharedPreferences.getString("name", "Student"));
        setupRecommendations();

        // Setup Drawer Toggle
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // CARD CLICKS
        cardBooks.setOnClickListener(v -> {
            Intent intent = new Intent(this, ViewBooksActivity.class);
            intent.putExtra("ROLE", "student");
            startActivity(intent);
        });
        
        cardRequest.setOnClickListener(v -> {
            Intent intent = new Intent(this, RequestBookActivity.class);
            intent.putExtra("EMAIL", userEmail);
            startActivity(intent);
        });
        
        cardIssued.setOnClickListener(v -> startActivity(new Intent(this, MyBooksActivity.class)));
        cardProfile.setOnClickListener(v -> openProfile());
        cardLeaderboard.setOnClickListener(v -> startActivity(new Intent(this, LeaderboardActivity.class)));

        // NAV MENU CLICKS
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if(id == R.id.menu_dashboard) drawerLayout.closeDrawers();
            else if(id == R.id.menu_books) {
                Intent intent = new Intent(this, ViewBooksActivity.class);
                intent.putExtra("ROLE", "student");
                startActivity(intent);
            }
            else if(id == R.id.menu_request) {
                Intent intent = new Intent(this, RequestBookActivity.class);
                intent.putExtra("EMAIL", userEmail);
                startActivity(intent);
            }
            else if(id == R.id.menu_issued) startActivity(new Intent(this, MyBooksActivity.class));
            else if(id == R.id.menu_profile) openProfile();
            else if(id == R.id.menu_logout) logout();
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // BOTTOM NAV CLICKS
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.bottom_home) return true;
            else if (id == R.id.bottom_books) {
                Intent intent = new Intent(this, ViewBooksActivity.class);
                intent.putExtra("ROLE", "student");
                startActivity(intent);
                return true;
            } else if (id == R.id.bottom_requests) {
                Intent intent = new Intent(this, RequestBookActivity.class);
                intent.putExtra("EMAIL", userEmail);
                startActivity(intent);
                return true;
            } else if (id == R.id.bottom_profile) {
                openProfile();
                return true;
            }
            return false;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
                else moveTaskToBack(true);
            }
        });
    }

    private void setUserStatus(String status) {
        if (userEmail != null && !userEmail.isEmpty()) {
            if ("Offline".equals(status)) {
                long timeSpentMillis = System.currentTimeMillis() - sessionStartTime;
                long minutes = timeSpentMillis / (1000 * 60);
                firestore.collection("users").document(userEmail)
                        .update("status", status, 
                                "lastSeen", FieldValue.serverTimestamp(),
                                "totalTimeSpent", FieldValue.increment(minutes));
            } else {
                firestore.collection("users").document(userEmail)
                        .update("status", status, "lastSeen", FieldValue.serverTimestamp());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileImage(userEmail);
        bottomNav.setSelectedItemId(R.id.bottom_home);
        setupRecommendations();
        setUserStatus("Online");
    }

    @Override
    protected void onPause() {
        super.onPause();
        setUserStatus("Offline");
    }

    private void openProfile() {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("NAME", sharedPreferences.getString("name", "Student"));
        intent.putExtra("EMAIL", userEmail);
        startActivity(intent);
    }

    private void logout() {
        setUserStatus("Offline");
        sharedPreferences.edit().clear().apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupRecommendations() {
        firestore.collection("books").get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                ArrayList<Book> cloudBooks = new ArrayList<>();
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    try {
                        cloudBooks.add(new Book(0, doc.getString("title"), doc.getString("author"), doc.getString("category"),
                                null, doc.getString("accession_no"), doc.getString("publisher"), doc.getString("edition"),
                                doc.getString("year"), doc.getString("pages"), doc.getString("purchase_date"),
                                doc.getString("mrp"), doc.getString("purchase_price"), doc.getString("discount"),
                                doc.contains("quantity") ? doc.getLong("quantity").intValue() : 1, null));
                    } catch (Exception e) {}
                }
                updateRecommendationUI(cloudBooks);
            }
        });
    }

    private void updateRecommendationUI(List<Book> books) {
        ArrayList<Book> listForDisplay = new ArrayList<>(books);
        Collections.shuffle(listForDisplay);
        int listSize = listForDisplay.size();
        List<Book> recommendedBooks = listForDisplay.subList(0, Math.min(listSize, 10));
        recommendationAdapter = new RecommendationAdapter(this, recommendedBooks);
        recyclerRecommendations.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerRecommendations.setAdapter(recommendationAdapter);
    }

    private void loadProfileImage(String email) {
        byte[] imageBytes = db.getUserProfileImage(email);
        if (imageBytes != null && navProfileImage != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            navProfileImage.setImageBitmap(bitmap);
        }
    }
}
