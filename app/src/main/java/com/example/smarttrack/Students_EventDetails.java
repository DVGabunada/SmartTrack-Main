package com.example.smarttrack;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class Students_EventDetails extends AppCompatActivity {

    private TextView eventTitleField, eventDescField, locationField, subjectField,selectedDateTextView;
    private TextView eventStartTimeField, eventEndTimeField;
    private Button eventBackButton;

    private String eventId;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_event_details);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Get eventId from Intent
        eventId = getIntent().getStringExtra("eventId");

        // Initialize UI elements
        eventTitleField = findViewById(R.id.eventTitleField);
        eventDescField = findViewById(R.id.eventDescField);
        locationField = findViewById(R.id.locationField);
        subjectField = findViewById(R.id.subjectField);
        selectedDateTextView = findViewById(R.id.selectedDateTextView);
        eventStartTimeField = findViewById(R.id.eventStartTimeField);
        eventEndTimeField = findViewById(R.id.eventEndTimeField);
        eventBackButton = findViewById(R.id.eventBackButton);

        // Fetch event details from Firestore
        if (eventId != null) {
            fetchEventDetails(eventId);
        } else {
            eventTitleField.setText("Error: Event ID is missing");
        }

        // Back button listener
        eventBackButton.setOnClickListener(v -> finish());
    }

    private void fetchEventDetails(String eventId) {
        DocumentReference eventRef = db.collection("events").document(eventId);

        eventRef.get().addOnSuccessListener(document -> {
            if (document.exists()) {
                // Get event details with null checks
                String title = document.getString("title");
                String description = document.getString("description");
                String location = document.getString("location");
                String eventDate = document.getString("eventDate");
                String startTime = document.getString("startTime");
                String endTime = document.getString("endTime");

                // Set default values if fields are missing
                eventTitleField.setText(title != null ? title : "No title available");
                eventDescField.setText(description != null ? description : "No description available");
                locationField.setText(location != null ? location : "No location specified");
                eventStartTimeField.setText(startTime != null ? startTime : "N/A");
                eventEndTimeField.setText(endTime != null ? endTime : "N/A");
                selectedDateTextView.setText(eventDate != null ? "Event Date: " + eventDate : "Event Date: N/A");

                // Fetch rooms from the event's subcollection
                fetchRoomFromEvent(eventId);
            } else {
                eventTitleField.setText("Error: Event not found");
            }
        }).addOnFailureListener(e -> {
            eventTitleField.setText("Error loading event");
        });
    }

    private void fetchRoomFromEvent(String eventId) {
        db.collection("events").document(eventId).collection("rooms")
                .limit(1) // Get only one room
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        String roomId = querySnapshot.getDocuments().get(0).getString("roomId");
                        if (roomId != null) {
                            fetchSubjectName(roomId);
                        } else {
                            subjectField.setText("No associated subject");
                        }
                    } else {
                        subjectField.setText("No associated subject");
                    }
                })
                .addOnFailureListener(e -> {
                    subjectField.setText("Error fetching subject");
                });
    }

    private void fetchSubjectName(String roomId) {
        DocumentReference roomRef = db.collection("rooms").document(roomId);

        roomRef.get().addOnSuccessListener(document -> {
            if (document.exists()) {
                String subjectName = document.getString("subjectName");
                subjectField.setText(subjectName != null ? subjectName : "No subject name found");
            } else {
                subjectField.setText("Subject not found");
            }
        }).addOnFailureListener(e -> {
            subjectField.setText("Error fetching subject");
        });
    }

}
