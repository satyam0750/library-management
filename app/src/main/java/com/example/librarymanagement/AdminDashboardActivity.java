package com.example.librarymanagement;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.AggregateSource;

import java.util.ArrayList;

public class AdminDashboardActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNav;
    private MaterialCardView cardBooks, cardStudents, cardRequests, cardInventoryAlert, cardIssue, cardReports, cardStaff;
    private TextView tvAdminName, tvPortalType, tvTotalBooks, tvTotalStudents, tvTotalIssued, tvPendingRequests, tvLowStockText;
    private TableLayout tableRecentRequests;
    private DatabaseHelper db;
    private SharedPreferences sharedPreferences;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("dark_mode", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();
        
        initViews();
        setupNavigation();
        updateNavHeader();
        setupCardClicks();
        
        // Apply Permissions and Update UI
        applyPermissions();
        
        // 1. Local Baseline
        loadLocalBaselineData();
        
        // 2. Start Live Snapshot Listeners
        listenForFirestoreLiveChanges();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navView);
        bottomNav = findViewById(R.id.bottomNav);

        tvPortalType = findViewById(R.id.tvPortalType);
        tvAdminName = findViewById(R.id.tvAdminName);
        tvTotalBooks = findViewById(R.id.tvTotalBooksCount);
        tvTotalStudents = findViewById(R.id.tvTotalStudentsCount);
        tvTotalIssued = findViewById(R.id.tvTotalIssuedCount);
        tvPendingRequests = findViewById(R.id.tvPendingRequestsCount);

        cardBooks = findViewById(R.id.cardBooks);
        cardStudents = findViewById(R.id.cardStudents);
        cardIssue = findViewById(R.id.cardIssue);
        cardRequests = findViewById(R.id.cardRequests);
        cardReports = findViewById(R.id.cardReports);
        cardStaff = findViewById(R.id.cardStaff);
        
        cardInventoryAlert = findViewById(R.id.cardInventoryAlert);
        tvLowStockText = findViewById(R.id.tvLowStockText);
        tableRecentRequests = findViewById(R.id.tableRecentRequests);
    }

    private void applyPermissions() {
        String role = sharedPreferences.getString("role", "assistant");
        String email = sharedPreferences.getString("email", "");
        String name = sharedPreferences.getString("name", "User");
        
        // Fix Welcome Text and Dashboard Title
        if ("admin".equalsIgnoreCase(role)) {
            tvAdminName.setText("Welcome, Admin");
            if (tvPortalType != null) tvPortalType.setText("Administrator Portal,");
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Admin Dashboard");
        } else {
            tvAdminName.setText("Welcome, Staff (" + name + ")");
            if (tvPortalType != null) tvPortalType.setText("Staff Portal,");
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Staff Dashboard");
        }
        
        if ("admin".equalsIgnoreCase(role)) {
            // Admin has full access
            cardStaff.setVisibility(View.VISIBLE);
            return;
        }

        // For Staff (Librarian/Assistant), check assigned tasks
        String tasks = db.getAssignedTasks(email);
        
        // Hide/Show cards based on permissions
        cardBooks.setVisibility(tasks.contains("Books") ? View.VISIBLE : View.GONE);
        cardStudents.setVisibility(tasks.contains("Students") ? View.VISIBLE : View.GONE);
        cardIssue.setVisibility(tasks.contains("Issue") ? View.VISIBLE : View.GONE);
        cardRequests.setVisibility(tasks.contains("Issue") ? View.VISIBLE : View.GONE); // Issue permission usually includes requests
        cardReports.setVisibility(tasks.contains("Fine") ? View.VISIBLE : View.GONE);
        
        // Staff should never see "Manage Staff" card
        cardStaff.setVisibility(View.GONE);

        // Filter Drawer Menu
        Menu navMenu = navigationView.getMenu();
        navMenu.findItem(R.id.menu_students).setVisible(tasks.contains("Students"));
        navMenu.findItem(R.id.menu_books).setVisible(tasks.contains("Books"));
        navMenu.findItem(R.id.menu_issue).setVisible(tasks.contains("Issue"));
        navMenu.findItem(R.id.menu_reports).setVisible(tasks.contains("Fine"));

        // Filter Bottom Nav
        Menu bottomMenu = bottomNav.getMenu();
        bottomMenu.findItem(R.id.bottom_books).setVisible(tasks.contains("Books"));
        bottomMenu.findItem(R.id.bottom_requests).setVisible(tasks.contains("Issue"));
    }

    private void loadLocalBaselineData() {
        SQLiteDatabase database = db.getReadableDatabase();
        Cursor cBooks = database.rawQuery("SELECT COUNT(*) FROM books", null);
        if (cBooks.moveToFirst()) tvTotalBooks.setText(String.valueOf(cBooks.getInt(0)));
        cBooks.close();

        Cursor cReq = database.rawQuery("SELECT COUNT(*) FROM requests WHERE status='Pending'", null);
        if (cReq.moveToFirst()) tvPendingRequests.setText(String.valueOf(cReq.getInt(0)));
        cReq.close();
    }

    private void listenForFirestoreLiveChanges() {
        // LIVE REQUESTS SYNC - Now with Ordering to ensure recent updates are visible
        firestore.collection("requests")
                .orderBy("id", Query.Direction.DESCENDING)
                .addSnapshotListener(this, (value, error) -> {
            if (error != null) {
                Log.e("AdminDashboard", "Error listening for requests", error);
                return;
            }
            if (value != null) {
                int pendingCount = 0;
                if (tableRecentRequests != null && tableRecentRequests.getChildCount() > 1) {
                    tableRecentRequests.removeViews(1, tableRecentRequests.getChildCount() - 1);
                }

                for (QueryDocumentSnapshot doc : value) {
                    String status = doc.getString("status");
                    if ("Pending".equalsIgnoreCase(status)) pendingCount++;
                    
                    // Removed rowCount limit to allow vertical scrolling within the dashboard
                    addFirestoreRequestRow(doc);
                }
                tvPendingRequests.setText(String.valueOf(pendingCount));
            }
        });

        // LIVE BOOKS SYNC
        firestore.collection("books").addSnapshotListener(this, (value, error) -> {
            if (value != null) {
                tvTotalBooks.setText(String.valueOf(value.size()));
                int lowStock = 0;
                for (QueryDocumentSnapshot doc : value) {
                    Long q = doc.getLong("quantity");
                    if (q != null && q < 3) lowStock++;
                }
                if (lowStock > 0) {
                    cardInventoryAlert.setVisibility(View.VISIBLE);
                    tvLowStockText.setText("Low Stock Alert: " + lowStock + " books remaining");
                } else {
                    cardInventoryAlert.setVisibility(View.GONE);
                }
            }
        });

        // LIVE STUDENTS SYNC
        firestore.collection("users").whereEqualTo("role", "student").addSnapshotListener(this, (value, error) -> {
            if (value != null) {
                tvTotalStudents.setText(String.valueOf(value.size()));
            }
        });

        // LIVE ISSUED BOOKS SYNC
        firestore.collection("loans").whereEqualTo("status", "Issued").addSnapshotListener(this, (value, error) -> {
            if (value != null) {
                tvTotalIssued.setText(String.valueOf(value.size()));
            }
        });
    }

    private void addFirestoreRequestRow(QueryDocumentSnapshot doc) {
        if (tableRecentRequests == null) return;
        TableRow row = new TableRow(this);
        row.setPadding(0, 10, 0, 10);

        TextView tvEmail = new TextView(this);
        String email = doc.getString("studentEmail");
        tvEmail.setText(email != null && email.contains("@") ? email.split("@")[0] : email);
        tvEmail.setTextSize(12);
        tvEmail.setPadding(5, 5, 5, 5);

        TextView tvBook = new TextView(this);
        tvBook.setText(doc.getString("bookName"));
        tvBook.setTextSize(12);
        tvBook.setPadding(5, 5, 5, 5);

        TextView tvStatus = new TextView(this);
        String status = doc.getString("status");
        tvStatus.setText(status);
        tvStatus.setTextSize(12);
        tvStatus.setPadding(5, 5, 5, 5);
        
        // Status color logic
        if ("Pending".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
            tvStatus.setTextColor(Color.RED);
        } else if ("Approved".equalsIgnoreCase(status)) {
            tvStatus.setTextColor(Color.GREEN);
        } else {
            tvStatus.setTextColor(Color.BLACK);
        }

        row.addView(tvEmail);
        row.addView(tvBook);
        row.addView(tvStatus);
        tableRecentRequests.addView(row);
    }

    private void setupNavigation() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_dashboard) drawerLayout.closeDrawer(GravityCompat.START);
            else if (id == R.id.menu_books) startActivity(new Intent(this, ViewBooksActivity.class));
            else if (id == R.id.menu_students) startActivity(new Intent(this, ManageStudentsActivity.class));
            else if (id == R.id.menu_issue) startActivity(new Intent(this, IssueBookActivity.class));
            else if (id == R.id.menu_history) startActivity(new Intent(this, HistoryActivity.class));
            else if (id == R.id.menu_reports) startActivity(new Intent(this, ReportsActivity.class));
            else if (id == R.id.menu_profile) startActivity(new Intent(this, ProfileActivity.class));
            else if (id == R.id.menu_logout) logout();
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.bottom_home) return true;
            else if (itemId == R.id.bottom_books) startActivity(new Intent(this, ViewBooksActivity.class));
            else if (itemId == R.id.bottom_requests) startActivity(new Intent(this, ManageRequestsActivity.class));
            else if (itemId == R.id.bottom_profile) startActivity(new Intent(this, ProfileActivity.class));
            return true;
        });
    }

    private void logout() {
        db.recordLogout(sharedPreferences.getString("email", ""));
        sharedPreferences.edit().clear().apply();
        startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void updateNavHeader() {
        View headerView = navigationView.getHeaderView(0);
        if (headerView != null) {
            TextView navName = headerView.findViewById(R.id.nav_name);
            TextView navEmail = headerView.findViewById(R.id.nav_email);
            String email = sharedPreferences.getString("email", "maravisatyam266@gmail.com");
            navName.setText(db.getUserName(email));
            navEmail.setText(email);
        }
    }

    private void setupCardClicks() {
        cardBooks.setOnClickListener(v -> startActivity(new Intent(this, ViewBooksActivity.class)));
        cardStudents.setOnClickListener(v -> startActivity(new Intent(this, ManageStudentsActivity.class)));
        cardIssue.setOnClickListener(v -> startActivity(new Intent(this, IssueBookActivity.class)));
        cardRequests.setOnClickListener(v -> startActivity(new Intent(this, ManageRequestsActivity.class)));
        cardReports.setOnClickListener(v -> startActivity(new Intent(this, ReportsActivity.class)));
        cardStaff.setOnClickListener(v -> startActivity(new Intent(this, ManageStaffActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.bottom_home);
        applyPermissions();
    }
}
