package com.example.librarymanagement;

public class Staff {
    private int id;
    private String name;
    private String email;
    private String role;
    private String status;
    private String tasks;
    private String lastSeen;
    private long totalTimeSpent;

    public Staff(int id, String name, String email, String role, String status, String tasks) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.status = status;
        this.tasks = tasks;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getTasks() { return tasks; }
    public void setStatus(String status) { this.status = status; }

    public String getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(String lastSeen) {
        this.lastSeen = lastSeen;
    }

    public long getTotalTimeSpent() {
        return totalTimeSpent;
    }

    public void setTotalTimeSpent(long totalTimeSpent) {
        this.totalTimeSpent = totalTimeSpent;
    }
}
