package com.example.smarttrack;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditAnnouncementActivity extends AppCompatActivity {

    private TextView roomText;
    private EditText titleInput, messageInput;
    private Button updateButton, cancelButton;
    private FirebaseFirestore db;
    private String announcementId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_announcement_form);

        db = FirebaseFirestore.getInstance();

        // Get announcement details from intent
        Intent intent = getIntent();
        announcementId = intent.getStringExtra("announcementId");
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        String roomCode = intent.getStringExtra("roomCode");

        // Initialize UI components
        titleInput = findViewById(R.id.titleInput);
        messageInput = findViewById(R.id.messageInput);
        updateButton = findViewById(R.id.updateEditButton);
        cancelButton = findViewById(R.id.cancelEditButton);
        roomText = findViewById(R.id.roomtext);

        // Set current values
        titleInput.setText(title);
        messageInput.setText(message);
        roomText.setText(roomCode);

        // Handle update button click
        updateButton.setOnClickListener(v -> updateAnnouncement());

        // Handle cancel button click
        cancelButton.setOnClickListener(v -> finish());
    }

    private void updateAnnouncement() {
        String updatedTitle = titleInput.getText().toString().trim();
        String updatedMessage = messageInput.getText().toString().trim();

        if (updatedTitle.isEmpty() || updatedMessage.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("announcement_title", updatedTitle);
        updates.put("announcement_message", updatedMessage);

        db.collection("announcements").document(announcementId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Announcement updated!", Toast.LENGTH_SHORT).show();
                    finish(); // Close activity after updating
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error updating: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
