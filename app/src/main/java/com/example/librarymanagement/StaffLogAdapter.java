package com.example.librarymanagement;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StaffLogAdapter extends RecyclerView.Adapter<StaffLogAdapter.ViewHolder> {

    private List<StaffLog> logList;

    public StaffLogAdapter(List<StaffLog> logList) {
        this.logList = logList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_staff_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StaffLog log = logList.get(position);
        holder.tvEmail.setText(log.getEmail());
        holder.tvTime.setText(log.getTimestamp());
        holder.tvAction.setText("Action: " + log.getAction());
        holder.tvTarget.setText("Target: " + log.getTarget());
    }

    @Override
    public int getItemCount() {
        return logList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmail, tvTime, tvAction, tvTarget;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmail = itemView.findViewById(R.id.tvLogEmail);
            tvTime = itemView.findViewById(R.id.tvLogTime);
            tvAction = itemView.findViewById(R.id.tvLogAction);
            tvTarget = itemView.findViewById(R.id.tvLogTarget);
        }
    }
}
