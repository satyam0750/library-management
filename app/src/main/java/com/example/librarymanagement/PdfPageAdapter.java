package com.example.librarymanagement;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.ViewHolder> {

    private PdfRenderer pdfRenderer;

    public PdfPageAdapter(PdfRenderer pdfRenderer) {
        this.pdfRenderer = pdfRenderer;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pdf_page, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Render page on demand to save memory
        PdfRenderer.Page page = pdfRenderer.openPage(position);
        
        // Create a bitmap with a reasonable size (adjust based on screen density for better quality)
        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
        
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        holder.ivPage.setImageBitmap(bitmap);
        
        page.close();
    }

    @Override
    public int getItemCount() {
        return pdfRenderer != null ? pdfRenderer.getPageCount() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPage = itemView.findViewById(R.id.ivPdfPage);
        }
    }
}
