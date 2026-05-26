package com.example.librarymanagement;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddBookActivity extends AppCompatActivity {

    private EditText etTitle, etAuthor, etAccessionNo, etQuantity;
    private EditText etPublisher, etEdition, etYearOfPublication, etPages, etPurchaseDate, etMrpPrice, etPurchasePrice, etDiscount, etPdfUrl;
    private Spinner spinnerCategory;
    private ImageView ivBookCover;
    private Button btnSelectImage, btnCaptureImage, btnSearchCover, btnScanISBN, btnAddBook, btnBulkImport, btnSelectPdf;
    private TextView tvPdfStatus;
    private DatabaseHelper db;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;
    private SharedPreferences sharedPreferences;
    private String currentUserEmail;
    private byte[] bookImage = null;
    private String bookPdfPath = null;
    private Uri pdfUri = null;
    private boolean isEditMode = false;
    private int bookId = -1;
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAMERA_REQUEST = 2;
    private List<String> categoryList;
    private ArrayAdapter<String> categoryAdapter;

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String isbn = result.getContents();
                    fetchBookDetailsByISBN(isbn);
                }
            });

    private final ActivityResultLauncher<Intent> excelPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    importExcelData(uri);
                }
            }
    );

    private final ActivityResultLauncher<Intent> pdfPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    pdfUri = result.getData().getData();
                    tvPdfStatus.setText("PDF Selected (Unsaved)");
                    tvPdfStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                    if (etPdfUrl != null) etPdfUrl.setText("");
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_book);

        db = new DatabaseHelper(this);
        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        currentUserEmail = sharedPreferences.getString("email", "Unknown");

        etTitle = findViewById(R.id.etTitle);
        etAuthor = findViewById(R.id.etAuthor);
        etAccessionNo = findViewById(R.id.etAccessionNo);
        etQuantity = findViewById(R.id.etQuantity);
        etPublisher = findViewById(R.id.etPublisher);
        etEdition = findViewById(R.id.etEdition);
        etYearOfPublication = findViewById(R.id.etYearOfPublication);
        etPages = findViewById(R.id.etPages);
        etPurchaseDate = findViewById(R.id.etPurchaseDate);
        etMrpPrice = findViewById(R.id.etMrpPrice);
        etPurchasePrice = findViewById(R.id.etPurchasePrice);
        etDiscount = findViewById(R.id.etDiscount);
        etPdfUrl = findViewById(R.id.etPdfUrl);

        spinnerCategory = findViewById(R.id.spinnerCategory);
        ivBookCover = findViewById(R.id.ivBookCover);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnCaptureImage = findViewById(R.id.btnCaptureImage);
        btnSearchCover = findViewById(R.id.btnSearchCover);
        btnScanISBN = findViewById(R.id.btnScanISBN);
        btnAddBook = findViewById(R.id.btnAddBook);
        btnBulkImport = findViewById(R.id.btnBulkImport);
        btnSelectPdf = findViewById(R.id.btnSelectPdf);
        tvPdfStatus = findViewById(R.id.tvPdfStatus);

        setupAutoCalculations();
        loadCategories();

        if (getIntent().getBooleanExtra("EDIT_MODE", false)) {
            isEditMode = true;
            bookId = getIntent().getIntExtra("BOOK_ID", -1);
            loadDataFromDatabase(bookId);
            btnAddBook.setText("Update Book");
            btnBulkImport.setVisibility(View.GONE);
        }

        btnSelectImage.setOnClickListener(v -> openGallery());
        if (btnCaptureImage != null) {
            btnCaptureImage.setOnClickListener(v -> openCamera());
        }
        
        btnScanISBN.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Scan a book ISBN barcode");
            options.setBeepEnabled(true);
            options.setOrientationLocked(false);
            barcodeLauncher.launch(options);
        });

        btnSearchCover.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter book title first", Toast.LENGTH_SHORT).show();
            } else {
                searchCoverOnline(title);
            }
        });

        btnSelectPdf.setOnClickListener(v -> openPdfPicker());
        btnAddBook.setOnClickListener(v -> saveOrUpdateBook());
        
        etPurchaseDate.setFocusable(false);
        etPurchaseDate.setOnClickListener(v -> showDatePickerDialog(etPurchaseDate));

        btnBulkImport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            excelPickerLauncher.launch(intent);
        });
    }

    private void setupAutoCalculations() {
        TextWatcher discountWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (etDiscount.isFocused()) {
                    calculatePurchasePrice();
                }
            }
        };

        TextWatcher priceWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (etPurchasePrice.isFocused()) {
                    calculateDiscount();
                }
            }
        };

        etDiscount.addTextChangedListener(discountWatcher);
        etPurchasePrice.addTextChangedListener(priceWatcher);
    }

    private void calculatePurchasePrice() {
        try {
            String mrpStr = etMrpPrice.getText().toString().trim();
            String discStr = etDiscount.getText().toString().trim();
            if (!mrpStr.isEmpty() && !discStr.isEmpty()) {
                double mrp = Double.parseDouble(mrpStr);
                double disc = Double.parseDouble(discStr);
                double pPrice = mrp - (mrp * disc / 100.0);
                etPurchasePrice.setText(String.format(Locale.getDefault(), "%.2f", pPrice));
            }
        } catch (Exception e) {
            Log.e("Calculation", "Error calculating price: " + e.getMessage());
        }
    }

    private void calculateDiscount() {
        try {
            String mrpStr = etMrpPrice.getText().toString().trim();
            String pPriceStr = etPurchasePrice.getText().toString().trim();
            if (!mrpStr.isEmpty() && !pPriceStr.isEmpty()) {
                double mrp = Double.parseDouble(mrpStr);
                double pPrice = Double.parseDouble(pPriceStr);
                if (mrp > 0) {
                    double disc = ((mrp - pPrice) / mrp) * 100.0;
                    etDiscount.setText(String.format(Locale.getDefault(), "%.2f", disc));
                }
            }
        } catch (Exception e) {
            Log.e("Calculation", "Error calculating discount: " + e.getMessage());
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, CAMERA_REQUEST);
    }

    private void fetchBookDetailsByISBN(String isbn) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Fetching book details for ISBN: " + isbn);
        pd.setCancelable(false);
        pd.show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                URL url = new URL("https://www.googleapis.com/books/v1/volumes?q=isbn:" + isbn);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                
                JSONObject json = new JSONObject(response.toString());
                if (json.getInt("totalItems") > 0) {
                    JSONObject item = json.getJSONArray("items").getJSONObject(0).getJSONObject("volumeInfo");
                    String title = item.optString("title", "");
                    JSONArray authors = item.optJSONArray("authors");
                    String author = authors != null ? authors.getString(0) : "Unknown";
                    String publisher = item.optString("publisher", "");
                    String year = item.optString("publishedDate", "").split("-")[0];
                    int pages = item.optInt("pageCount", 0);
                    
                    String coverUrl = "";
                    if (item.has("imageLinks")) {
                        coverUrl = item.getJSONObject("imageLinks").optString("thumbnail", "").replace("http:", "https:");
                    }

                    Bitmap bitmap = null;
                    if (!coverUrl.isEmpty()) {
                        bitmap = downloadBitmap(coverUrl);
                    }

                    final Bitmap finalBitmap = bitmap;
                    runOnUiThread(() -> {
                        pd.dismiss();
                        etTitle.setText(title);
                        etAuthor.setText(author);
                        etPublisher.setText(publisher);
                        etYearOfPublication.setText(year);
                        if (pages > 0) etPages.setText(String.valueOf(pages));
                        if (finalBitmap != null) {
                            ivBookCover.setImageBitmap(finalBitmap);
                            bookImage = getBytesFromBitmap(finalBitmap);
                        }
                        Toast.makeText(this, "Book details fetched!", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> {
                        pd.dismiss();
                        Toast.makeText(this, "No book found for this ISBN", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(this, "Error fetching details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void searchCoverOnline(String title) {
        String author = etAuthor.getText().toString().trim();
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Searching multiple sources...");
        pd.setCancelable(false);
        pd.show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                String encodedTitle = URLEncoder.encode(title, "UTF-8");
                String encodedAuthor = URLEncoder.encode(author, "UTF-8");
                Bitmap foundBitmap = null;

                // 1. Try Google Books API for ISBN and Thumbnail
                String gBooksUrl = "https://www.googleapis.com/books/v1/volumes?q=intitle:" + encodedTitle + (author.isEmpty() ? "" : "+inauthor:" + encodedAuthor) + "&maxResults=1";
                JSONObject gJson = fetchJson(gBooksUrl);
                
                if (gJson != null && gJson.optInt("totalItems", 0) > 0) {
                    JSONObject volumeInfo = gJson.getJSONArray("items").getJSONObject(0).getJSONObject("volumeInfo");
                    
                    // Try to get ISBN for high-res Amazon fallback
                    String isbnFound = "";
                    if (volumeInfo.has("industryIdentifiers")) {
                        JSONArray ids = volumeInfo.getJSONArray("industryIdentifiers");
                        for (int i = 0; i < ids.length(); i++) {
                            if (ids.getJSONObject(i).optString("type").contains("ISBN_13")) {
                                isbnFound = ids.getJSONObject(i).optString("identifier");
                            }
                        }
                    }

                    // Try Amazon (Best Quality)
                    if (!isbnFound.isEmpty()) {
                        foundBitmap = downloadBitmap("https://images-na.ssl-images-amazon.com/images/P/" + isbnFound + ".01.LZZZZZZZ.jpg");
                    }

                    // Try Google's own thumbnail if Amazon fails
                    if (foundBitmap == null && volumeInfo.has("imageLinks")) {
                        String gImg = volumeInfo.getJSONObject("imageLinks").optString("thumbnail", "").replace("http:", "https:");
                        foundBitmap = downloadBitmap(gImg.replace("zoom=1", "zoom=2"));
                    }
                }

                // 2. Try OpenLibrary Search if still nothing
                if (foundBitmap == null) {
                    String olSearchUrl = "https://openlibrary.org/search.json?title=" + encodedTitle + "&limit=1";
                    JSONObject olJson = fetchJson(olSearchUrl);
                    if (olJson != null && olJson.optInt("numFound", 0) > 0) {
                        String coverId = olJson.getJSONArray("docs").getJSONObject(0).optString("cover_i", "");
                        if (!coverId.isEmpty()) {
                            foundBitmap = downloadBitmap("https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg");
                        }
                    }
                }

                // 3. Last Fallback: Direct OpenLibrary Title URL
                if (foundBitmap == null) {
                    foundBitmap = downloadBitmap("https://covers.openlibrary.org/b/title/" + encodedTitle.replace("+", "%20") + "-L.jpg");
                }

                final Bitmap finalBmp = foundBitmap;
                runOnUiThread(() -> {
                    pd.dismiss();
                    if (finalBmp != null) {
                        ivBookCover.setImageBitmap(finalBmp);
                        bookImage = getBytesFromBitmap(finalBmp);
                        Toast.makeText(this, "Cover found and applied!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No cover found online. Try manual selection.", Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private JSONObject fetchJson(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap downloadBitmap(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            int response = conn.getResponseCode();
            if (response == HttpURLConnection.HTTP_OK) {
                InputStream in = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(in);
                if (bmp != null && bmp.getWidth() > 1) return bmp; // Ignore 1x1 empty images
            }
        } catch (Exception e) {
            Log.e("AddBook", "Download failed: " + e.getMessage());
        }
        return null;
    }

    private void showDatePickerDialog(EditText editText) {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    String date = year1 + "-" + String.format("%02d", (monthOfYear + 1)) + "-" + String.format("%02d", dayOfMonth);
                    editText.setText(date);
                }, year, month, day);
        datePickerDialog.show();
    }

    private void loadCategories() {
        categoryList = db.getAllCategories();
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryList);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);
        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (categoryList.get(position).equals("+ Add New Category")) showAddCategoryDialog();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void showAddCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Category");
        final EditText input = new EditText(this);
        builder.setView(input);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String newCat = input.getText().toString().trim();
            if (!newCat.isEmpty() && db.insertCategory(newCat)) loadCategories();
        });
        builder.show();
    }

    private void loadDataFromDatabase(int id) {
        Book book = db.getBookById(id);
        if (book != null) {
            etTitle.setText(book.getTitle());
            etAuthor.setText(book.getAuthor());
            etAccessionNo.setText(book.getAccessionNo());
            etPublisher.setText(book.getPublisher());
            etEdition.setText(book.getEdition());
            etYearOfPublication.setText(book.getYearOfPublication());
            etPages.setText(book.getPages());
            etMrpPrice.setText(book.getMrpPrice());
            etPurchasePrice.setText(book.getPurchasePrice());
            etDiscount.setText(book.getDiscount());
            etPurchaseDate.setText(book.getPurchaseDate());
            etQuantity.setText(String.valueOf(book.getQuantity()));
            
            // Selection with delay and better matching
            if (book.getCategory() != null) {
                final String targetCategory = book.getCategory().trim();
                spinnerCategory.postDelayed(() -> {
                    if (categoryList != null && categoryAdapter != null) {
                        int foundPosition = -1;
                        for (int i = 0; i < categoryList.size(); i++) {
                            if (categoryList.get(i).trim().equalsIgnoreCase(targetCategory)) {
                                foundPosition = i;
                                break;
                            }
                        }

                        if (foundPosition != -1) {
                            spinnerCategory.setSelection(foundPosition, false);
                        } else if (!targetCategory.isEmpty()) {
                            // Category missing in list, add it temporarily
                            int insertIndex = Math.max(0, categoryList.size() - 1);
                            categoryList.add(insertIndex, targetCategory);
                            categoryAdapter.notifyDataSetChanged();
                            spinnerCategory.setSelection(insertIndex, false);
                        }
                    }
                }, 200);
            }

            bookPdfPath = book.getPdfPath();
            if (bookPdfPath != null && !bookPdfPath.isEmpty()) {
                if (bookPdfPath.startsWith("http") && etPdfUrl != null) etPdfUrl.setText(bookPdfPath);
                tvPdfStatus.setText("PDF Already Added");
                tvPdfStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }

            if (book.getCoverImage() != null) {
                ivBookCover.setImageBitmap(BitmapFactory.decodeByteArray(book.getCoverImage(), 0, book.getCoverImage().length));
            }
        }
    }

    private void saveOrUpdateBook() {
        final String title = etTitle.getText().toString().trim();
        final String author = etAuthor.getText().toString().trim();
        final String accession = etAccessionNo.getText().toString().trim();
        final String publisher = etPublisher.getText().toString().trim();
        final String edition = etEdition.getText().toString().trim();
        final String year = etYearOfPublication.getText().toString().trim();
        final String pages = etPages.getText().toString().trim();
        final String mrp = etMrpPrice.getText().toString().trim();
        final String pPrice = etPurchasePrice.getText().toString().trim();
        final String disc = etDiscount.getText().toString().trim();
        final String pDate = etPurchaseDate.getText().toString().trim();
        
        if (spinnerCategory.getSelectedItem() == null) return;
        final String cat = spinnerCategory.getSelectedItem().toString();

        final String manualUrl = etPdfUrl != null ? etPdfUrl.getText().toString().trim() : "";
        int qty = 1;
        try { qty = Integer.parseInt(etQuantity.getText().toString().trim()); } catch (Exception e) {}

        if (title.isEmpty() || accession.isEmpty()) {
            Toast.makeText(this, "Title and Accession are required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cat.equals("Select Category")) {
            Toast.makeText(this, "Please select a valid category", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!manualUrl.isEmpty()) {
            saveBookData(title, author, cat, accession, publisher, edition, year, pages, pDate, mrp, pPrice, disc, qty, manualUrl);
        } else if (pdfUri != null) {
            uploadPdfToFirebase(pdfUri, title, author, cat, accession, publisher, edition, year, pages, pDate, mrp, pPrice, disc, qty);
        } else {
            saveBookData(title, author, cat, accession, publisher, edition, year, pages, pDate, mrp, pPrice, disc, qty, bookPdfPath);
        }
    }

    private void uploadPdfToFirebase(Uri uri, String title, String author, String cat, String accession, String publisher, String edition, String year, String pages, String pDate, String mrp, String pPrice, String disc, int qty) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Uploading PDF...");
        pd.setMessage("Please wait while we upload the book PDF to cloud storage.");
        pd.setCancelable(false);
        pd.show();

        StorageReference ref = storage.getReference().child("book_pdfs/" + accession + "_" + System.currentTimeMillis() + ".pdf");
        ref.putFile(uri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    pd.dismiss();
                    String firebaseUrl = downloadUri.toString();
                    saveBookData(title, author, cat, accession, publisher, edition, year, pages, pDate, mrp, pPrice, disc, qty, firebaseUrl);
                }))
                .addOnFailureListener(e -> {
                    pd.dismiss();
                    Toast.makeText(this, "PDF Upload Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                })
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    pd.setMessage("Uploaded " + (int) progress + "%");
                });
    }

    private void saveBookData(String title, String author, String cat, String accession, String publisher, String edition, String year, String pages, String pDate, String mrp, String pPrice, String disc, int qty, String pdfPath) {
        Map<String, Object> bookMap = new HashMap<>();
        bookMap.put("title", title);
        bookMap.put("author", author);
        bookMap.put("category", cat);
        bookMap.put("accession_no", accession);
        bookMap.put("publisher", publisher);
        bookMap.put("edition", edition);
        bookMap.put("year", year);
        bookMap.put("pages", pages);
        bookMap.put("mrp", mrp);
        bookMap.put("purchase_price", pPrice);
        bookMap.put("discount", disc);
        bookMap.put("purchase_date", pDate);
        bookMap.put("quantity", qty);
        bookMap.put("book_pdf_path", pdfPath);

        if (isEditMode) {
            if (db.updateBookFull(bookId, title, author, cat, bookImage, accession, publisher, edition, year, pages, pDate, mrp, pPrice, disc, qty, pdfPath)) {
                firestore.collection("books").document(accession).set(bookMap, SetOptions.merge());
                Toast.makeText(this, "Book Updated Successfully", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            if (db.insertBookFull(title, author, cat, bookImage, accession, publisher, edition, year, pages, pDate, mrp, pPrice, disc, qty, pdfPath)) {
                firestore.collection("books").document(accession).set(bookMap);
                Toast.makeText(this, "Book Added Successfully", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void importExcelData(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Workbook workbook = WorkbookFactory.create(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();

            int titleIdx = -1, authorIdx = -1, catIdx = -1, accIdx = -1, qtyIdx = -1;
            int pubIdx = -1, edIdx = -1, yearIdx = -1, pageIdx = -1, dateIdx = -1, mrpIdx = -1, pPriceIdx = -1, discIdx = -1;

            boolean headerFound = false;
            int importedCount = 0;

            for (Row row : sheet) {
                if (!headerFound) {
                    for (Cell cell : row) {
                        String val = dataFormatter.formatCellValue(cell).trim().toLowerCase();
                        if (val.contains("title")) titleIdx = cell.getColumnIndex();
                        else if (val.contains("author")) authorIdx = cell.getColumnIndex();
                        else if (val.contains("category")) catIdx = cell.getColumnIndex();
                        else if (val.contains("accession")) accIdx = cell.getColumnIndex();
                        else if (val.contains("quantity") || val.contains("qty")) qtyIdx = cell.getColumnIndex();
                        else if (val.contains("publisher")) pubIdx = cell.getColumnIndex();
                        else if (val.contains("edition")) edIdx = cell.getColumnIndex();
                        else if (val.contains("year")) yearIdx = cell.getColumnIndex();
                        else if (val.contains("pages")) pageIdx = cell.getColumnIndex();
                        else if (val.contains("date")) dateIdx = cell.getColumnIndex();
                        else if (val.contains("mrp")) mrpIdx = cell.getColumnIndex();
                        else if (val.contains("price")) pPriceIdx = cell.getColumnIndex();
                        else if (val.contains("discount")) discIdx = cell.getColumnIndex();
                    }
                    if (titleIdx != -1 && accIdx != -1) headerFound = true;
                    continue;
                }

                String title = titleIdx != -1 ? dataFormatter.formatCellValue(row.getCell(titleIdx)) : "";
                String accession = accIdx != -1 ? dataFormatter.formatCellValue(row.getCell(accIdx)) : "";
                if (title.isEmpty() || accession.isEmpty()) continue;

                String author = authorIdx != -1 ? dataFormatter.formatCellValue(row.getCell(authorIdx)) : "";
                String category = catIdx != -1 ? dataFormatter.formatCellValue(row.getCell(catIdx)) : "General";
                String publisher = pubIdx != -1 ? dataFormatter.formatCellValue(row.getCell(pubIdx)) : "";
                 String edition = edIdx != -1 ? dataFormatter.formatCellValue(row.getCell(edIdx)) : "";
                String year = yearIdx != -1 ? dataFormatter.formatCellValue(row.getCell(yearIdx)) : "";
                String pages = pageIdx != -1 ? dataFormatter.formatCellValue(row.getCell(pageIdx)) : "";
                String date = dateIdx != -1 ? dataFormatter.formatCellValue(row.getCell(dateIdx)) : "";
                String mrp = mrpIdx != -1 ? dataFormatter.formatCellValue(row.getCell(mrpIdx)) : "";
                String pPrice = pPriceIdx != -1 ? dataFormatter.formatCellValue(row.getCell(pPriceIdx)) : "";
                String discount = discIdx != -1 ? dataFormatter.formatCellValue(row.getCell(discIdx)) : "";
                int qty = 1;
                if (qtyIdx != -1) {
                    try { qty = (int) Double.parseDouble(dataFormatter.formatCellValue(row.getCell(qtyIdx))); } catch (Exception ignored) {}
                }

                if (db.insertBookFull(title, author, category, null, accession, publisher, edition, year, pages, date, mrp, pPrice, discount, qty, null)) {
                    importedCount++;
                    Map<String, Object> bookMap = new HashMap<>();
                    bookMap.put("title", title);
                    bookMap.put("author", author);
                    bookMap.put("category", category);
                    bookMap.put("accession_no", accession);
                    bookMap.put("publisher", publisher);
                    bookMap.put("edition", edition);
                    bookMap.put("year", year);
                    bookMap.put("pages", pages);
                    bookMap.put("mrp", mrp);
                    bookMap.put("purchase_price", pPrice);
                    bookMap.put("discount", discount);
                    bookMap.put("purchase_date", date);
                    bookMap.put("quantity", qty);
                    firestore.collection("books").document(accession).set(bookMap);
                }
            }
            workbook.close();
            Toast.makeText(this, "Imported " + importedCount + " books", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Import Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        pdfPickerLauncher.launch(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                ivBookCover.setImageBitmap(bitmap);
                bookImage = getBytesFromBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK && data != null) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            ivBookCover.setImageBitmap(photo);
            bookImage = getBytesFromBitmap(photo);
        }
    }

    private byte[] getBytesFromBitmap(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
        return stream.toByteArray();
    }
}
