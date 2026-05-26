package com.example.librarymanagement;

public class Book {

    int id;
    String title, author, category;
    byte[] coverImage;
    String accessionNo;
    String publisher, edition, yearOfPublication, pages, purchaseDate, mrpPrice, purchasePrice, discount;
    int quantity;
    byte[] pdf;
    String pdfPath; // Added for large PDF support

    public Book(int id, String title, String author, String category, byte[] coverImage, String accessionNo,
                String publisher, String edition, String yearOfPublication, String pages, String purchaseDate,
                String mrpPrice, String purchasePrice, String discount, int quantity, byte[] pdf) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.category = category;
        this.coverImage = coverImage;
        this.accessionNo = accessionNo;
        this.publisher = publisher;
        this.edition = edition;
        this.yearOfPublication = yearOfPublication;
        this.pages = pages;
        this.purchaseDate = purchaseDate;
        this.mrpPrice = mrpPrice;
        this.purchasePrice = purchasePrice;
        this.discount = discount;
        this.quantity = quantity;
        this.pdf = pdf;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getCategory() { return category; }
    public byte[] getCoverImage() { return coverImage; }
    public String getAccessionNo() { return accessionNo; }
    public String getPublisher() { return publisher; }
    public String getEdition() { return edition; }
    public String getYearOfPublication() { return yearOfPublication; }
    public String getPages() { return pages; }
    public String getPurchaseDate() { return purchaseDate; }
    public String getMrpPrice() { return mrpPrice; }
    public String getPurchasePrice() { return purchasePrice; }
    public String getDiscount() { return discount; }
    public int getQuantity() { return quantity; }
    public byte[] getPdf() { return pdf; }
    
    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }

}
