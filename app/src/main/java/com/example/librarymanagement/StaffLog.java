package com.example.librarymanagement;

public class StaffLog {
    private String email;
    private String action;
    private String target;
    private String timestamp;

    public StaffLog(String email, String action, String target, String timestamp) {
        this.email = email;
        this.action = action;
        this.target = target;
        this.timestamp = timestamp;
    }

    public String getEmail() { return email; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getTimestamp() { return timestamp; }
}
