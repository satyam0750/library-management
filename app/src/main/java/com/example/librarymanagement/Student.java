package com.example.librarymanagement;

public class Student {

    int id;
    String name;
    String email;
    String studentId;
    String status; // Online/Offline
    String lastSeen;
    long timeSpent; // Total time spent in seconds or minutes

    public Student() {} // Required for Firestore

    public Student(int id, String name, String email, String studentId){
        this.id = id;
        this.name = name;
        this.email = email;
        this.studentId = studentId;
        this.status = "Offline";
    }

    public int getId(){
        return id;
    }

    public String getName(){
        return name;
    }

    public String getEmail(){
        return email;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(String lastSeen) {
        this.lastSeen = lastSeen;
    }

    public long getTimeSpent() {
        return timeSpent;
    }

    public void setTimeSpent(long timeSpent) {
        this.timeSpent = timeSpent;
    }
}
