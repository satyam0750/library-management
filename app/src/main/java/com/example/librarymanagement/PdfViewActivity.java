package com.example.librarymanagement;

import android.app.ProgressDialog;
import android.graphics.pdf.PdfRenderer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

public class PdfViewActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_view);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.pdfRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        String pdfPath = getIntent().getStringExtra("PDF_PATH");
        byte[] pdfData = getIntent().getByteArrayExtra("PDF_DATA");

        if (pdfPath != null && !pdfPath.isEmpty()) {
            if (pdfPath.startsWith("http")) {
                if (pdfPath.contains("firebasestorage.googleapis.com")) {
                    downloadFirebasePdf(pdfPath);
                } else {
                    new DownloadGenericPdfTask(this).execute(pdfPath);
                }
            } else {
                try {
                    renderPdf(new File(pdfPath));
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error loading local PDF", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (pdfData != null) {
            try {
                File tempFile = new File(getCacheDir(), "temp_view.pdf");
                FileOutputStream fos = new FileOutputStream(tempFile);
                fos.write(pdfData);
                fos.close();
                renderPdf(tempFile);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading PDF data", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No PDF path found", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadFirebasePdf(String url) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Downloading from Firebase...");
        pd.setCancelable(false);
        pd.show();

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference ref = storage.getReferenceFromUrl(url);

        try {
            File localFile = File.createTempFile("temp_fb", ".pdf", getCacheDir());
            ref.getFile(localFile).addOnSuccessListener(taskSnapshot -> {
                pd.dismiss();
                try {
                    renderPdf(localFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).addOnFailureListener(e -> {
                pd.dismiss();
                Toast.makeText(this, "Firebase Download Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        } catch (IOException e) {
            pd.dismiss();
            e.printStackTrace();
        }
    }

    private static class DownloadGenericPdfTask extends AsyncTask<String, Void, File> {
        private WeakReference<PdfViewActivity> activityReference;
        ProgressDialog pd;

        DownloadGenericPdfTask(PdfViewActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            PdfViewActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) return;
            pd = new ProgressDialog(activity);
            pd.setMessage("Downloading PDF from URL...");
            pd.setCancelable(false);
            pd.show();
        }

        @Override
        protected File doInBackground(String... strings) {
            PdfViewActivity activity = activityReference.get();
            if (activity == null) return null;
            try {
                URL url = new URL(strings[0]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                if (urlConnection.getResponseCode() == 200) {
                    File file = new File(activity.getCacheDir(), "temp_downloaded.pdf");
                    InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                    FileOutputStream outputStream = new FileOutputStream(file);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                    }
                    outputStream.close();
                    inputStream.close();
                    return file;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(File file) {
            PdfViewActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) return;
            if (pd != null && pd.isShowing()) pd.dismiss();
            
            if (file != null) {
                try {
                    activity.renderPdf(file);
                } catch (IOException e) {
                    Toast.makeText(activity, "Error rendering PDF", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(activity, "Failed to download PDF", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void renderPdf(File file) throws IOException {
        if (parcelFileDescriptor != null) parcelFileDescriptor.close();
        if (pdfRenderer != null) pdfRenderer.close();

        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        pdfRenderer = new PdfRenderer(parcelFileDescriptor);

        // Memory efficient approach: Don't render all pages at once.
        // Instead, pass the renderer to the adapter and render pages on-demand.
        PdfPageAdapter adapter = new PdfPageAdapter(pdfRenderer);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (pdfRenderer != null) pdfRenderer.close();
            if (parcelFileDescriptor != null) parcelFileDescriptor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
