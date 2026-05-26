package com.example.librarymanagement;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private Context context;
    private List<LeaderboardActivity.StudentScore> scoreList;

    public LeaderboardAdapter(Context context, List<LeaderboardActivity.StudentScore> scoreList) {
        this.context = context;
        this.scoreList = scoreList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_leaderboard, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LeaderboardActivity.StudentScore score = scoreList.get(position);
        holder.tvRank.setText(String.valueOf(score.rank));
        holder.tvName.setText(score.name);
        holder.tvBooksRead.setText(score.booksRead + " Books Read");

        if (score.rank == 1) {
            holder.ivBadge.setVisibility(View.VISIBLE);
            holder.tvRank.setBackgroundResource(R.drawable.circle_orange); // Gold/Orange for 1st
        } else if (score.rank == 2) {
            holder.ivBadge.setVisibility(View.GONE);
            holder.tvRank.setBackgroundResource(R.drawable.circle_purple); // Silver-ish
        } else {
            holder.ivBadge.setVisibility(View.GONE);
            holder.tvRank.setBackgroundResource(R.drawable.circle_blue);
        }
    }

    @Override
    public int getItemCount() {
        return scoreList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvName, tvBooksRead;
        ImageView ivBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvName = itemView.findViewById(R.id.tvStudentName);
            tvBooksRead = itemView.findViewById(R.id.tvBooksRead);
            ivBadge = itemView.findViewById(R.id.ivBadge);
        }
    }
}
