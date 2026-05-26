package com.example.librarymanagement;

public class Transaction {
    int id;
    String member, book, type, date, returnDate;
    String studentEmail, bookAccession, studentId; // Added studentId for identification
    double fineAmount;

    public Transaction(int id, String member, String book, String type, String date) {
        this.id = id;
        this.member = member;
        this.book = book;
        this.type = type;
        this.date = date;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getMember() { return member != null ? member : "Unknown"; }
    public String getBook() { return book != null ? book : "Unknown Book"; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDate() { return date; }
    
    public String getReturnDate() { return returnDate; }
    public void setReturnDate(String returnDate) { this.returnDate = returnDate; }
    
    public double getFineAmount() { return fineAmount; }
    public void setFineAmount(double fineAmount) { this.fineAmount = fineAmount; }

    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }

    public String getBookAccession() { return bookAccession; }
    public void setBookAccession(String bookAccession) { this.bookAccession = bookAccession; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
}
