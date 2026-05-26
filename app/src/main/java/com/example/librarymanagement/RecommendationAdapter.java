package com.example.librarymanagement;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class RecommendationAdapter extends RecyclerView.Adapter<RecommendationAdapter.ViewHolder> {

    private Context context;
    private List<Book> bookList;

    public RecommendationAdapter(Context context, List<Book> bookList) {
        this.context = context;
        this.bookList = bookList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_recommended_book, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Book book = bookList.get(position);
        holder.tvTitle.setText(book.getTitle());
        holder.tvAuthor.setText(book.getAuthor());

        if (book.getCoverImage() != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(book.getCoverImage(), 0, book.getCoverImage().length);
            holder.imgBook.setImageBitmap(bitmap);
        } else {
            // Auto-fetch cover from OpenLibrary based on Title
            String coverUrl = "https://covers.openlibrary.org/b/title/" + book.getTitle().replace(" ", "%20") + "-M.jpg";
            Glide.with(context)
                    .load(coverUrl)
                    .placeholder(R.drawable.book1)
                    .error(R.drawable.book1)
                    .into(holder.imgBook);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, BookDetailsActivity.class);
            intent.putExtra("BOOK_ID", book.getId());
            intent.putExtra("TITLE", book.getTitle());
            intent.putExtra("AUTHOR", book.getAuthor());
            intent.putExtra("CATEGORY", book.getCategory());
            intent.putExtra("ACCESSION", book.getAccessionNo());
            intent.putExtra("PUBLISHER", book.getPublisher());
            intent.putExtra("EDITION", book.getEdition());
            intent.putExtra("YEAR", book.getYearOfPublication());
            intent.putExtra("PAGES", book.getPages());
            intent.putExtra("PRICE", book.getMrpPrice());
            intent.putExtra("COVER", book.getCoverImage());
            intent.putExtra("PDF_PATH", book.getPdfPath());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return Math.min(bookList.size(), 10); // Show max 10 recommendations
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgBook;
        TextView tvTitle, tvAuthor;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgBook = itemView.findViewById(R.id.imgBook);
            tvTitle = itemView.findViewById(R.id.bookTitle);
            tvAuthor = itemView.findViewById(R.id.bookAuthor);
        }
    }
}
