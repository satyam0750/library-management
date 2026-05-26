package com.example.librarymanagement;

public class Request {
    private String id; // Changed to String for Realtime DB key
    private String bookName;
    private String authorName;
    private String studentEmail;
    private String status;
    private String date;

    public Request() {
        // Default constructor required for calls to DataSnapshot.getValue(Request.class)
    }

    public Request(String id, String bookName, String authorName, String studentEmail, String status, String date) {
        this.id = id;
        this.bookName = bookName;
        this.authorName = authorName;
        this.studentEmail = studentEmail;
        this.status = status;
        this.date = date;
    }

    // Standard getters and setters for Firebase
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getBookName() { return bookName; }
    public void setBookName(String bookName) { this.bookName = bookName; }
    
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    
    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}
