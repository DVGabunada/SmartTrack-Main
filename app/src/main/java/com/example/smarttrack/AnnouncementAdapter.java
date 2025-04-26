package com.example.smarttrack;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AnnouncementAdapter extends RecyclerView.Adapter<AnnouncementAdapter.AnnouncementViewHolder> {

    private List<Announcement> announcementList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onEditClick(Announcement announcement);
        void onDeleteClick(Announcement announcement);
    }

    public AnnouncementAdapter(List<Announcement> announcementList, OnItemClickListener listener) {
        this.announcementList = announcementList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AnnouncementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.announcement_list, parent, false);
        return new AnnouncementViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AnnouncementViewHolder holder, int position) {
        Announcement announcement = announcementList.get(position);
        holder.titleTextView.setText(announcement.getAnnouncement_title());
        holder.teacherTextView.setText("By: " + announcement.getTeacherName());
        holder.timestampTextView.setText(announcement.getFormattedTimestamp());
        holder.roomCodeTextView.setText(announcement.getRoomCode());
        holder.editButton.setOnClickListener(v -> listener.onEditClick(announcement));
        holder.deleteButton.setOnClickListener(v -> listener.onDeleteClick(announcement));
    }

    @Override
    public int getItemCount() {
        return announcementList.size();
    }

    public static class AnnouncementViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, teacherTextView, timestampTextView, roomCodeTextView;
        Button editButton, deleteButton;

        public AnnouncementViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.title);
            teacherTextView = itemView.findViewById(R.id.teacher);
            timestampTextView = itemView.findViewById(R.id.timestamp);
            editButton = itemView.findViewById(R.id.editAnnouncement);
            deleteButton = itemView.findViewById(R.id.deleteAnnouncement);
            roomCodeTextView = itemView.findViewById(R.id.roomCode);
        }
    }

    public void setAnnouncements(List<Announcement> announcements) {
        this.announcementList = announcements;
        notifyDataSetChanged();
    }
}

