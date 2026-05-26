package com.example.librarymanagement.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.librarymanagement.Book;
import com.example.librarymanagement.LeaderboardActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "library.db";
    private static final int DB_VERSION = 15;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,email TEXT,password TEXT,role TEXT, profile_image BLOB, student_id TEXT, status TEXT DEFAULT 'Active', assigned_tasks TEXT)");
        db.execSQL("CREATE TABLE books(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT,author TEXT,category TEXT, cover_image BLOB, accession_no TEXT UNIQUE, " +
                "publisher TEXT, edition TEXT, year_of_publication TEXT, pages TEXT, purchase_date TEXT, mrp_price TEXT, purchase_price TEXT, discount TEXT, available_quantity INTEGER DEFAULT 1, book_pdf_path TEXT, book_pdf_blob BLOB)");
        db.execSQL("CREATE TABLE issues(id INTEGER PRIMARY KEY AUTOINCREMENT,book TEXT,student TEXT,date TEXT)");
        db.execSQL("CREATE TABLE requests(id INTEGER PRIMARY KEY AUTOINCREMENT, book_name TEXT, author_name TEXT, student_email TEXT, status TEXT, date TEXT)");
        db.execSQL("CREATE TABLE members(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,email TEXT)");
        db.execSQL("CREATE TABLE loans(id INTEGER PRIMARY KEY AUTOINCREMENT,book_id INTEGER,member_id INTEGER,date TEXT,status TEXT, return_date TEXT, fine_amount REAL DEFAULT 0)");
        db.execSQL("CREATE TABLE staff_logs(id INTEGER PRIMARY KEY AUTOINCREMENT, staff_email TEXT, action TEXT, target TEXT, timestamp TEXT)");
        db.execSQL("CREATE TABLE staff_attendance(id INTEGER PRIMARY KEY AUTOINCREMENT, staff_email TEXT, login_time TEXT, logout_time TEXT, date TEXT)");
        db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY AUTOINCREMENT, cat_name TEXT UNIQUE)");

        ContentValues cv = new ContentValues();
        cv.put("name","Admin");
        cv.put("email","maravisatyam266@gmail.com");
        cv.put("password","admin123");
        cv.put("role","admin");
        cv.put("student_id", "ADM-000");
        cv.put("status", "Active");
        db.insert("users",null,cv);

        String[] defaults = {"Computer Science", "Mathematics", "Physics", "History", "Fiction", "Biography"};
        for (String cat : defaults) {
            ContentValues c = new ContentValues();
            c.put("cat_name", cat);
            db.insert("categories", null, c);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 15) {
            db.execSQL("DROP TABLE IF EXISTS books");
            db.execSQL("CREATE TABLE books(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT,author TEXT,category TEXT, cover_image BLOB, accession_no TEXT UNIQUE, " +
                    "publisher TEXT, edition TEXT, year_of_publication TEXT, pages TEXT, purchase_date TEXT, mrp_price TEXT, purchase_price TEXT, discount TEXT, available_quantity INTEGER DEFAULT 1, book_pdf_path TEXT, book_pdf_blob BLOB)");
        }
    }

    public boolean insertLoan(int bookId, int studentId, String date, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("book_id", bookId);
        cv.put("member_id", studentId);
        cv.put("date", date);
        cv.put("status", status);
        long result = db.insert("loans", null, cv);
        return result != -1;
    }

    public ArrayList<String> getAllCategories() {
        ArrayList<String> list = new ArrayList<>();
        list.add("Select Category");
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT cat_name FROM categories ORDER BY cat_name ASC", null);
        if (cursor.moveToFirst()) {
            do { list.add(cursor.getString(0)); } while (cursor.moveToNext());
        }
        cursor.close();
        list.add("+ Add New Category");
        return list;
    }

    public boolean insertCategory(String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("cat_name", name);
        long result = db.insertWithOnConflict("categories", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        return result != -1;
    }

    public boolean insertBookFull(String title, String author, String category, byte[] image, String accession,
                                 String publisher, String edition, String year, String pages, String pDate,
                                 String mrp, String pPrice, String discount, int quantity, String pdfPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("author", author);
        cv.put("category", category);
        cv.put("cover_image", image);
        cv.put("accession_no", accession);
        cv.put("publisher", publisher);
        cv.put("edition", edition);
        cv.put("year_of_publication", year);
        cv.put("pages", pages);
        cv.put("purchase_date", pDate);
        cv.put("mrp_price", mrp);
        cv.put("purchase_price", pPrice);
        cv.put("discount", discount);
        cv.put("available_quantity", quantity);
        cv.put("book_pdf_path", pdfPath);

        long result = db.insertWithOnConflict("books", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    public void syncBook(String title, String author, String category, String accession,
                        String publisher, String edition, String year, String pages, String pDate,
                        String mrp, String pPrice, String discount, int quantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("author", author);
        cv.put("category", category);
        cv.put("accession_no", accession);
        cv.put("publisher", publisher);
        cv.put("edition", edition);
        cv.put("year_of_publication", year);
        cv.put("pages", pages);
        cv.put("purchase_date", pDate);
        cv.put("mrp_price", mrp);
        cv.put("purchase_price", pPrice);
        cv.put("discount", discount);
        cv.put("available_quantity", quantity);

        int id = getBookIdByAccession(accession);
        if (id != -1) {
            db.update("books", cv, "id=?", new String[]{String.valueOf(id)});
        } else {
            db.insert("books", null, cv);
        }
    }

    public boolean updateBookFull(int id, String title, String author, String category, byte[] image, String accession,
                                 String publisher, String edition, String year, String pages, String pDate,
                                 String mrp, String pPrice, String discount, int quantity, String pdfPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("author", author);
        cv.put("category", category);
        if (image != null) cv.put("cover_image", image);
        cv.put("accession_no", accession);
        cv.put("publisher", publisher);
        cv.put("edition", edition);
        cv.put("year_of_publication", year);
        cv.put("pages", pages);
        cv.put("purchase_date", pDate);
        cv.put("mrp_price", mrp);
        cv.put("purchase_price", pPrice);
        cv.put("discount", discount);
        cv.put("available_quantity", quantity);
        if (pdfPath != null) cv.put("book_pdf_path", pdfPath);

        int result = db.update("books", cv, "id=?", new String[]{String.valueOf(id)});
        return result > 0;
    }

    public Book getBookById(int bookId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM books WHERE id=?", new String[]{String.valueOf(bookId)});
        if (cursor.moveToFirst()) {
            Book book = new Book(
                    cursor.getInt(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getBlob(4), cursor.getString(5),
                    cursor.getString(6), cursor.getString(7), cursor.getString(8),
                    cursor.getString(9), cursor.getString(10), cursor.getString(11),
                    cursor.getString(12), cursor.getString(13), cursor.getInt(14), null
            );
            book.setPdfPath(cursor.getString(15));
            cursor.close();
            return book;
        }
        cursor.close();
        return null;
    }

    public Book getBookByAccession(String accession) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM books WHERE accession_no=?", new String[]{accession});
        if (cursor.moveToFirst()) {
            Book book = new Book(
                    cursor.getInt(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getBlob(4), cursor.getString(5),
                    cursor.getString(6), cursor.getString(7), cursor.getString(8),
                    cursor.getString(9), cursor.getString(10), cursor.getString(11),
                    cursor.getString(12), cursor.getString(13), cursor.getInt(14), null
            );
            book.setPdfPath(cursor.getString(15));
            cursor.close();
            return book;
        }
        cursor.close();
        return null;
    }

    public ArrayList<Book> getAllBooks() {
        ArrayList<Book> bookList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM books", null);
        if (cursor.moveToFirst()) {
            do {
                Book book = new Book(
                        cursor.getInt(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getBlob(4), cursor.getString(5),
                        cursor.getString(6), cursor.getString(7), cursor.getString(8),
                        cursor.getString(9), cursor.getString(10), cursor.getString(11),
                        cursor.getString(12), cursor.getString(13), cursor.getInt(14), null
                );
                book.setPdfPath(cursor.getString(15));
                bookList.add(book);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return bookList;
    }

    public void updateBookQuantity(int bookId, int delta) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE books SET available_quantity = available_quantity + (" + delta + ") WHERE id = " + bookId);
    }

    public boolean insertBookRequest(String title, String author, String email, String date) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("book_name", title);
        cv.put("author_name", author);
        cv.put("student_email", email);
        cv.put("status", "Pending");
        cv.put("date", date);
        long result = db.insert("requests", null, cv);
        return result != -1;
    }

    public boolean updateRequestStatus(String email, String bookName, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", status);
        return db.update("requests", cv, "student_email=? AND book_name=?", new String[]{email, bookName}) > 0;
    }

    public boolean recordLogin(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("staff_email", email);
        cv.put("login_time", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
        cv.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        long r = db.insert("staff_attendance", null, cv);
        return r != -1;
    }

    public void recordLogout(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        ContentValues cv = new ContentValues();
        cv.put("logout_time", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
        db.update("staff_attendance", cv, "staff_email=? AND date=? AND logout_time IS NULL", new String[]{email, date});
    }

    public String getUserName(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT name FROM users WHERE email=?", new String[]{email});
        if (cursor.moveToFirst()) {
            String name = cursor.getString(0);
            cursor.close();
            return name;
        }
        cursor.close();
        return "";
    }

    public byte[] getUserProfileImage(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT profile_image FROM users WHERE email=?", new String[]{email});
        if (cursor.moveToFirst()) {
            byte[] img = cursor.getBlob(0);
            cursor.close();
            return img;
        }
        cursor.close();
        return null;
    }

    public boolean deleteBook(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete("books", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteBookByAccession(String accession) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete("books", "accession_no=?", new String[]{accession}) > 0;
    }

    public byte[] getBookPdf(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT book_pdf_blob FROM books WHERE id=?", new String[]{String.valueOf(id)});
        if (cursor.moveToFirst()) {
            byte[] pdf = cursor.getBlob(0);
            cursor.close();
            return pdf;
        }
        cursor.close();
        return null;
    }

    public boolean hasBookPdf(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM books WHERE id=? AND (book_pdf_blob IS NOT NULL OR (book_pdf_path IS NOT NULL AND book_pdf_path != ''))", new String[]{String.valueOf(id)});
        boolean has = cursor.getCount() > 0;
        cursor.close();
        return has;
    }

    public boolean checkUserExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM users WHERE email=?", new String[]{email});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public boolean updatePassword(String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("password", password);
        return db.update("users", cv, "email=?", new String[]{email}) > 0;
    }

    public void logActivity(String email, String action, String target) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("staff_email", email);
        cv.put("action", action);
        cv.put("target", target);
        cv.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        db.insert("staff_logs", null, cv);
    }

    public int getBookIdByAccession(String accession) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM books WHERE accession_no=?", new String[]{accession});
        if (cursor.moveToFirst()) {
            int id = cursor.getInt(0);
            cursor.close();
            return id;
        }
        cursor.close();
        return -1;
    }

    public int getBookIdByAccessionFromTitle(String title) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM books WHERE title=?", new String[]{title});
        if (cursor.moveToFirst()) {
            int id = cursor.getInt(0);
            cursor.close();
            return id;
        }
        cursor.close();
        return -1;
    }

    public int getUserIdByStudentId(String studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM users WHERE student_id=?", new String[]{studentId});
        if (cursor.moveToFirst()) {
            int id = cursor.getInt(0);
            cursor.close();
            return id;
        }
        cursor.close();
        return -1;
    }

    public int getUserIdByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM users WHERE email=?", new String[]{email});
        if (cursor.moveToFirst()) {
            int id = cursor.getInt(0);
            cursor.close();
            return id;
        }
        cursor.close();
        return -1;
    }

    public String getStudentEmailByStudentId(String studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT email FROM users WHERE student_id=?", new String[]{studentId});
        if (cursor.moveToFirst()) {
            String email = cursor.getString(0);
            cursor.close();
            return email;
        }
        cursor.close();
        return null;
    }

    public String checkUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT role FROM users WHERE email=? AND password=?", new String[]{email, password});
        if (cursor.moveToFirst()) {
            String role = cursor.getString(0);
            cursor.close();
            return role;
        }
        cursor.close();
        return null;
    }

    public boolean isBookIssuedToUser(String email, int bookId) {
        SQLiteDatabase db = this.getReadableDatabase();
        int userId = getUserIdByEmail(email);
        if (userId != -1) {
            Cursor cursor = db.rawQuery("SELECT id FROM loans WHERE member_id=? AND book_id=? AND status='Issued'", 
                    new String[]{String.valueOf(userId), String.valueOf(bookId)});
            boolean issued = cursor.getCount() > 0;
            cursor.close();
            return issued;
        }
        return false;
    }

    public boolean hasPendingRequest(String email, String bookName) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM requests WHERE student_email=? AND book_name=? AND status='Pending'",
                new String[]{email, bookName});
        boolean has = cursor.getCount() > 0;
        cursor.close();
        return has;
    }

    public String getStudentId(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT student_id FROM users WHERE email=?", new String[]{email});
        if (cursor.moveToFirst()) {
            String id = cursor.getString(0);
            cursor.close();
            return id;
        }
        cursor.close();
        return "";
    }

    public boolean insertUserFull(String name, String email, String password, String role, String studentId, String status, String tasks) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        cv.put("password", password);
        cv.put("role", role);
        cv.put("student_id", studentId);
        cv.put("status", status);
        cv.put("assigned_tasks", tasks);
        long result = db.insertWithOnConflict("users", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    public boolean insertUserWithId(String name, String email, String password, String role, String studentId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        cv.put("password", password);
        cv.put("role", role);
        cv.put("student_id", studentId);
        cv.put("status", "Active");
        long result = db.insertWithOnConflict("users", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    public boolean insertStaff(String name, String email, String password, String role, String tasks) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        cv.put("password", password);
        cv.put("role", role);
        cv.put("assigned_tasks", tasks);
        cv.put("status", "Active");
        long result = db.insert("users", null, cv);
        return result != -1;
    }

    public boolean updateStaff(int id, String name, String email, String password, String role, String tasks) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        if (password != null && !password.isEmpty()) {
            cv.put("password", password);
        }
        cv.put("role", role);
        cv.put("assigned_tasks", tasks);
        int result = db.update("users", cv, "id=?", new String[]{String.valueOf(id)});
        return result > 0;
    }

    public int getBookQuantity(int bookId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT available_quantity FROM books WHERE id=?", new String[]{String.valueOf(bookId)});
        int quantity = 0;
        if (cursor.moveToFirst()) {
            quantity = cursor.getInt(0);
        }
        cursor.close();
        return quantity;
    }

    public boolean deleteStudent(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete("users", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteUserByEmail(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete("users", "email=?", new String[]{email}) > 0;
    }

    public ArrayList<Book> getIssuedBooksForStudent(String email) {
        ArrayList<Book> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        int userId = getUserIdByEmail(email);
        if (userId != -1) {
            String query = "SELECT b.* FROM books b INNER JOIN loans l ON b.id = l.book_id WHERE l.member_id = ? AND l.status = 'Issued'";
            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});
            if (cursor.moveToFirst()) {
                do {
                    Book book = new Book(
                            cursor.getInt(0), cursor.getString(1), cursor.getString(2),
                            cursor.getString(3), cursor.getBlob(4), cursor.getString(5),
                            cursor.getString(6), cursor.getString(7), cursor.getString(8),
                            cursor.getString(9), cursor.getString(10), cursor.getString(11),
                            cursor.getString(12), cursor.getString(13), cursor.getInt(14), null
                    );
                    book.setPdfPath(cursor.getString(15));
                    list.add(book);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return list;
    }

    public boolean updateUserProfileImage(String email, byte[] image) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("profile_image", image);
        return db.update("users", cv, "email=?", new String[]{email}) > 0;
    }

    public String getUserRole(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT role FROM users WHERE email=?", new String[]{email});
        String role = "";
        if (cursor.moveToFirst()) {
            role = cursor.getString(0);
        }
        cursor.close();
        return role;
    }

    public String getAssignedTasks(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT assigned_tasks FROM users WHERE email=?", new String[]{email});
        String tasks = "";
        if (cursor.moveToFirst()) {
            tasks = cursor.getString(0);
        }
        cursor.close();
        return tasks != null ? tasks : "";
    }

    public double getTotalFine(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        double total = 0;
        int userId = getUserIdByEmail(email);
        if (userId != -1) {
            Cursor cursor = db.rawQuery("SELECT SUM(fine_amount) FROM loans WHERE member_id=?", new String[]{String.valueOf(userId)});
            if (cursor.moveToFirst()) {
                total = cursor.getDouble(0);
            }
            cursor.close();
        }
        return total;
    }

    public List<LeaderboardActivity.StudentScore> getLeaderboardData() {
        List<LeaderboardActivity.StudentScore> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT u.name, COUNT(l.id) as read_count " +
                "FROM users u " +
                "JOIN loans l ON u.id = l.member_id " +
                "WHERE u.role = 'student' " +
                "GROUP BY u.id " +
                "ORDER BY read_count DESC LIMIT 10";
        Cursor cursor = db.rawQuery(query, null);
        int rank = 1;
        if (cursor.moveToFirst()) {
            do {
                list.add(new LeaderboardActivity.StudentScore(cursor.getString(0), cursor.getInt(1), rank++));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public boolean updateUserName(String email, String newName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", newName);
        return db.update("users", cv, "email=?", new String[]{email}) > 0;
    }

    public int getLoanId(String email, String accession) {
        SQLiteDatabase db = this.getReadableDatabase();
        int userId = getUserIdByEmail(email);
        int bookId = getBookIdByAccession(accession);
        if (userId != -1 && bookId != -1) {
            Cursor cursor = db.rawQuery("SELECT id FROM loans WHERE member_id=? AND book_id=? AND status='Issued'",
                    new String[]{String.valueOf(userId), String.valueOf(bookId)});
            if (cursor.moveToFirst()) {
                int id = cursor.getInt(0);
                cursor.close();
                return id;
            }
            cursor.close();
        }
        return -1;
    }

    public boolean returnBook(int loanId, String email, String accession, String returnDate, double fine) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", "Returned");
        cv.put("return_date", returnDate);
        cv.put("fine_amount", fine);

        int result = 0;
        if (loanId > 0) {
            result = db.update("loans", cv, "id=?", new String[]{String.valueOf(loanId)});
        }

        if (result == 0 && email != null && accession != null) {
            int userId = getUserIdByEmail(email);
            if (userId == -1) userId = getUserIdByStudentId(email);
            int bookId = getBookIdByAccession(accession);
            
            if (userId != -1 && bookId != -1) {
                result = db.update("loans", cv, "member_id=? AND book_id=? AND status='Issued'", 
                        new String[]{String.valueOf(userId), String.valueOf(bookId)});
            }
        }
        
        if (result > 0) {
            int bookId = -1;
            if (loanId > 0) {
                Cursor c = db.rawQuery("SELECT book_id FROM loans WHERE id=?", new String[]{String.valueOf(loanId)});
                if (c.moveToFirst()) bookId = c.getInt(0);
                c.close();
            } else {
                bookId = getBookIdByAccession(accession);
            }
            if (bookId != -1) updateBookQuantity(bookId, 1);
            return true;
        }
        return false;
    }
}
