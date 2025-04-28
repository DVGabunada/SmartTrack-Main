package com.example.smarttrack;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TeacherMonthSelectorAdapter extends RecyclerView.Adapter<TeacherMonthSelectorAdapter.MonthViewHolder> {
    private final List<String> months;
    private final OnMonthClickListener listener;

    public interface OnMonthClickListener {
        void onMonthClick(String month);
    }

    public TeacherMonthSelectorAdapter(List<String> months, OnMonthClickListener listener) {
        this.months = months;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MonthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_teachers_month, parent, false);
        return new MonthViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MonthViewHolder holder, int position) {
        String month = months.get(position);
        holder.monthName.setText(month);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMonthClick(month);
            }
        });
    }

    @Override
    public int getItemCount() {
        return months.size();
    }

    public static class MonthViewHolder extends RecyclerView.ViewHolder {
        TextView monthName;

        public MonthViewHolder(@NonNull View itemView) {
            super(itemView);
            monthName = itemView.findViewById(R.id.monthName);
        }
    }
}
