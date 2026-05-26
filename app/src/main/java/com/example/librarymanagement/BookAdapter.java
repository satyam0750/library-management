package com.example.librarymanagement;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.*;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.librarymanagement.database.DatabaseHelper;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.ViewHolder> implements Filterable {

    Context context;
    List<Book> list;
    List<Book> listFull;
    DatabaseHelper db;
    String userRole;
    private String dbUrl = "https://library-management-5e49a-default-rtdb.asia-southeast1.firebasedatabase.app";

    public BookAdapter(Context context, List<Book> list) {
        this.context = context;
        this.list = list;
        this.listFull = new ArrayList<>(list);
        this.db = new DatabaseHelper(context);
        
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        this.userRole = prefs.getString("role", "student");
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, author, category, tvAccession;
        ImageView imgBook;
        ImageButton delete, edit;

        public ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.bookTitle);
            author = itemView.findViewById(R.id.bookAuthor);
            category = itemView.findViewById(R.id.bookCategory);
            tvAccession = itemView.findViewById(R.id.tvAccessionNo);
            imgBook = itemView.findViewById(R.id.imgBook);
            delete = itemView.findViewById(R.id.btnDeleteBook);
            edit = itemView.findViewById(R.id.btnEditBook);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_book, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Book b = list.get(position);

        holder.title.setText(b.getTitle());
        holder.author.setText("By: " + b.getAuthor());
        holder.category.setText(b.getCategory());
        holder.tvAccession.setText(b.getAccessionNo());

        if (b.getCoverImage() != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(b.getCoverImage(), 0, b.getCoverImage().length);
            holder.imgBook.setImageBitmap(bitmap);
        } else {
            // Auto-fetch cover from OpenLibrary based on Title
            String coverUrl = "https://covers.openlibrary.org/b/title/" + b.getTitle().replace(" ", "%20") + "-M.jpg";
            Glide.with(context)
                    .load(coverUrl)
                    .placeholder(R.drawable.book1)
                    .error(R.drawable.book1)
                    .into(holder.imgBook);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, BookDetailsActivity.class);
            intent.putExtra("BOOK_ID", b.getId());
            intent.putExtra("ACCESSION", b.getAccessionNo());
            context.startActivity(intent);
        });

        if ("admin".equalsIgnoreCase(userRole) || "librarian".equalsIgnoreCase(userRole)) {
            holder.delete.setVisibility(View.VISIBLE);
            holder.edit.setVisibility(View.VISIBLE);
            
            holder.delete.setOnClickListener(v -> {
                // 1. Delete from Firestore (Cloud 1)
                FirebaseFirestore.getInstance().collection("books")
                        .document(b.getAccessionNo()) // Assuming accession_no is doc ID
                        .delete();
                
                // Fallback delete using where query if doc ID is not accession_no
                FirebaseFirestore.getInstance().collection("books")
                        .whereEqualTo("accession_no", b.getAccessionNo())
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                doc.getReference().delete();
                            }
                        });

                // 2. Delete from Realtime Database (Cloud 2 - Singapore Server)
                FirebaseDatabase.getInstance(dbUrl).getReference("books")
                        .child(b.getAccessionNo())
                        .removeValue();

                // 3. Delete from SQLite (Local)
                if (db.deleteBookByAccession(b.getAccessionNo())) {
                    int adapterPos = holder.getAdapterPosition();
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        list.remove(adapterPos);
                        // Update listFull for filter
                        for (int i=0; i<listFull.size(); i++) {
                            if (listFull.get(i).getAccessionNo().equals(b.getAccessionNo())) {
                                listFull.remove(i);
                                break;
                            }
                        }
                        notifyItemRemoved(adapterPos);
                        notifyItemRangeChanged(adapterPos, list.size());
                        Toast.makeText(context, "Book Deleted Everywhere", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            holder.edit.setOnClickListener(v -> {
                Intent intent = new Intent(context, AddBookActivity.class);
                intent.putExtra("EDIT_MODE", true);
                intent.putExtra("BOOK_ID", b.getId());
                context.startActivity(intent);
            });
        } else {
            holder.delete.setVisibility(View.GONE);
            holder.edit.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    @Override
    public Filter getFilter() {
        return bookFilter;
    }

    private Filter bookFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Book> filteredList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(listFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (Book item : listFull) {
                    if (item.getTitle().toLowerCase().contains(filterPattern) ||
                        item.getAuthor().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            list.clear();
            list.addAll((List) results.values);
            notifyDataSetChanged();
        }
    };
}
