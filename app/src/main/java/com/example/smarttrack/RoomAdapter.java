package com.example.smarttrack;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    private List<String> roomList;

    public RoomAdapter(List<String> roomList) {
        this.roomList = roomList;
    }

    @Override
    public RoomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RoomViewHolder holder, int position) {
        String room = roomList.get(position);
        holder.roomTextView.setText(room);  // Set the room info as subjectCode - section
    }

    @Override
    public int getItemCount() {
        return roomList.size();
    }

    public static class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView roomTextView;

        public RoomViewHolder(View itemView) {
            super(itemView);
            roomTextView = itemView.findViewById(android.R.id.text1);  // Get reference to the TextView
        }
    }
}
