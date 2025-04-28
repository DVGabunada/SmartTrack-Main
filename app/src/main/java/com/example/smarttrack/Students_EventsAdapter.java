package com.example.smarttrack;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class Students_EventsAdapter extends RecyclerView.Adapter<Students_EventsAdapter.EventViewHolder> {

    private final List<Event> events;
    private final Context context;
    private final OnEventClickListener eventClickListener;

    // Interface for event click handling
    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    // Constructor with click listener
    public Students_EventsAdapter(Context context, List<Event> events, OnEventClickListener listener) {
        this.context = context;
        this.events = events;
        this.eventClickListener = listener;
    }
    public Students_EventsAdapter(Context context, List<Event> events) {
        this.context = context;
        this.events = events;
        this.eventClickListener = null; // No event click handling
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.activity_student_event_item, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);

        // Log event details for debugging
        System.out.println("Binding event: " + event.getTitle() + " on " + event.getEventDate());

        // Extract event day from date (YYYY-MM-DD format)
        String eventDate = event.getEventDate();
        try {
            String[] dateParts = eventDate.split("-");
            String day = dateParts[2];
            holder.dateTextView.setText(day);
        } catch (Exception e) {
            holder.dateTextView.setText("00");
        }

        holder.titleTextView.setText(event.getTitle());
        holder.locationTextView.setText(event.getLocation());
        holder.timeTextView.setText(event.isWholeDay()
                ? "ALL DAY"
                : event.getStartTime() + " - " + event.getEndTime());

        // Handle event click to open Students_EventDetails
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, Students_EventDetails.class);
            intent.putExtra("eventId", event.getId()); // Pass event ID
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    public static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, dateTextView, locationTextView, timeTextView;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.eventTitle);
            dateTextView = itemView.findViewById(R.id.eventDate);
            locationTextView = itemView.findViewById(R.id.eventLocation);
            timeTextView = itemView.findViewById(R.id.eventTime);
        }
    }
}
