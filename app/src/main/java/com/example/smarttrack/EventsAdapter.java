package com.example.smarttrack;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.*;

public class EventsAdapter extends RecyclerView.Adapter<EventsAdapter.EventViewHolder> {

    private final List<Event> events;
    private final Context context;
    private final OnEventActionListener eventActionListener;

    // Updated Constructor
    public EventsAdapter(Context context, List<Event> events, OnEventActionListener eventActionListener) {
        this.context = context;
        this.events = events;
        this.eventActionListener = eventActionListener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.activity_event_item, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);

        // Display event day only using SimpleDateFormat
        String eventDate = event.getEventDate();
        if (eventDate != null && eventDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = sdf.parse(eventDate); // Convert string to Date

                SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());
                String day = dayFormat.format(date); // Extract day

                holder.dateTextView.setText(day); // Display only the day
            } catch (Exception e) {
                holder.dateTextView.setText("00"); // Set default if parsing fails
                e.printStackTrace();
            }
        } else {
            holder.dateTextView.setText("00"); // Default value if date is invalid
        }



        holder.titleTextView.setText(event.getTitle());
        holder.locationTextView.setText(event.getLocation());
        holder.timeTextView.setText(event.isWholeDay()
                ? "ALL DAY"
                : event.getStartTime() + " - " + event.getEndTime());

    // Handle edit event
        holder.editEvent.setOnClickListener(v -> {
            Intent intent = new Intent(context, Teachers_EditEvent.class);
            intent.putExtra("eventId", event.getId());
            intent.putExtra("eventTitle", event.getTitle());
            intent.putExtra("eventDescription", event.getDescription());
            intent.putExtra("eventLocation", event.getLocation());
            intent.putExtra("eventDate", event.getEventDate());
            intent.putExtra("eventStartTime", event.getStartTime());
            intent.putExtra("eventEndTime", event.getEndTime());
            intent.putExtra("notify", event.isNotify());
            intent.putExtra("wholeDay", event.isWholeDay());

            // Pass the list of selected rooms instead of students
            ArrayList<String> eventRooms = (ArrayList<String>) event.getRooms();
            intent.putStringArrayListExtra("eventRooms", eventRooms);

            context.startActivity(intent);
        });

        // Handle delete event
        holder.deleteEvent.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Event")
                    .setMessage("Are you sure you want to delete this event?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (eventActionListener != null) {
                            eventActionListener.onDeleteEvent(event);
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }


    @Override
    public int getItemCount() {
        return events.size();
    }

    public static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView, dateTextView, locationTextView, timeTextView;
        ImageView editEvent, deleteEvent;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.eventTitle);
            dateTextView = itemView.findViewById(R.id.eventDate);
            locationTextView = itemView.findViewById(R.id.eventLocation);
            timeTextView = itemView.findViewById(R.id.eventTime);
            editEvent = itemView.findViewById(R.id.editEvent);
            deleteEvent = itemView.findViewById(R.id.deleteEvent);
        }
    }

    // Interface for handling delete actions
    public interface OnEventActionListener {
        void onEditEvent(Event event);
        void onDeleteEvent(Event event);
    }
}
