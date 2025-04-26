package com.example.smarttrack;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnouncementActivity extends AppCompatActivity{

    public static final String TAG = "AnnouncementActivity";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private LinearLayout announcementLayout;
    private TextView noAnnounceTextView;

    //Toolbars
    private ImageView homeIcon, roomIcon, reportIcon, scheduleIcon;
    private DrawerLayout drawerLayout;

    private NavigationView navigationView;
    private TextView navUsername, navIdNumber;

    Button createButton, sendButton, cancelButton, editButton, deleteButton, cancelEditButton;
    View separator;
    EditText titleInput, messageInput;
    Spinner spinner;

    private String uid;
    private String userType = "";

    private Announcement announcement;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcement);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        uid = getIntent().getStringExtra("uid");



        fetchUserDetailed(uid);
        setupToolbarAndUI();
        setupUI();
        fetchValidRooms();

        createButton = findViewById(R.id.createButton);
        separator = findViewById(R.id.separator);

        // SECTION: modal ---------------
        FrameLayout announcementFormContainer = findViewById(R.id.announcementFormContainer);
        View formView = getLayoutInflater().inflate(R.layout.activity_announcement_form, announcementFormContainer, false);
        announcementFormContainer.addView(formView);

        View modalFormView = getLayoutInflater().inflate(R.layout.activity_announcement_form, null);
        announcementFormContainer.addView(modalFormView);
        announcementFormContainer.setVisibility(View.GONE);

        // SECTION: form ---------------
        titleInput = modalFormView.findViewById(R.id.titleInput);
        messageInput = modalFormView.findViewById(R.id.messageInput);
        sendButton = modalFormView.findViewById(R.id.sendButton);
        cancelButton = modalFormView.findViewById(R.id.cancelButton); // Fix: Get from modalFormView
        spinner = modalFormView.findViewById(R.id.spinner);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedRoomCode = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "Selected room code: " + selectedRoomCode);
                // Optionally store this if you want to reuse it
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Optional: handle no selection
            }
        });


        createButton.setOnClickListener(v -> announcementFormContainer.setVisibility(View.VISIBLE));
        cancelButton.setOnClickListener(v -> {Log.d("AnnouncementActivity", "Cancel button clicked");
            announcementFormContainer.setVisibility(View.INVISIBLE);});

        // FEATURE: Send Button for Creating Announcements
        sendButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String message = messageInput.getText().toString().trim();
            String selectedRoomCode = spinner.getSelectedItem().toString();

            if (title.isEmpty() || message.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            String teacherName = navUsername.getText().toString();

            // Create announcement
            Announcement announcement = new Announcement();
            announcement.setAnnouncement_title(title);
            announcement.setAnnouncement_message(message);
            announcement.setTeacherName(teacherName);
            announcement.setTeacherUid(uid);
            announcement.setTimestamp(new com.google.firebase.Timestamp(new java.util.Date()));
            announcement.setRoomCode(selectedRoomCode);

            // Upload to Firestore
            db.collection("announcements")
                    .add(announcement)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Announcement sent!", Toast.LENGTH_SHORT).show();
                        announcementFormContainer.setVisibility(View.GONE);
                        titleInput.setText("");
                        messageInput.setText("");
                        fetchAnnouncements(); // Refresh list
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to send announcement: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });





    }


    private void fetchUserDetailed(String uid) {
        FirebaseFirestore.getInstance().collection("students").document(uid).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String fullName = document.getString("firstName") + " " + document.getString("lastName");
                        navUsername.setText(fullName);
                        navIdNumber.setText(document.getString("idNumber"));
                        userType = "Student";
                        hideButtonsForStudents();
                    } else {
                        FirebaseFirestore.getInstance().collection("teachers").document(uid).get()
                                .addOnSuccessListener(teacherDoc -> {
                                    if (teacherDoc.exists()) {
                                        userType = "Teacher";
                                        String fullName = teacherDoc.getString("firstName") + " " + teacherDoc.getString("lastName");
                                        navUsername.setText(fullName);
                                        navIdNumber.setText(teacherDoc.getString("idNumber")); // optional if you store it
                                    } else {
                                        userType = "Admin";
                                    }
                                    fetchAnnouncements();
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Error fetching teacher details", e));
                        return;
                    }

                    fetchAnnouncements();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching student details", e));
    }



    private void hideButtonsForStudents() {
        if (userType.equals("Student")) {
            createButton.setVisibility(View.GONE);
            separator.setVisibility(View.GONE);
        }
    }



    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(AnnouncementActivity.this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    //the floating toolbar at the bottom
    private void setupToolbarAndUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("Announcements");
        announcementLayout = findViewById(R.id.announcementLayout);
        noAnnounceTextView = findViewById(R.id.noAnnounceTextView);

        homeIcon = findViewById(R.id.homeIcon);
        roomIcon = findViewById(R.id.roomIcon);
        scheduleIcon = findViewById(R.id.scheduleIcon);
        reportIcon = findViewById(R.id.reportIcon);

        drawerLayout = findViewById(R.id.drawerLayout);

        navigationView = findViewById(R.id.navigationView);
        navUsername = navigationView.findViewById(R.id.navUsername);
        navIdNumber = navigationView.findViewById(R.id.navIdNumber);

        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> logout());


        homeIcon.setOnClickListener(v -> startActivity(new Intent(this, Students_Room.class).putExtra("uid", uid)));
        roomIcon.setOnClickListener(v -> startActivity(new Intent(this, Students_Room.class).putExtra("uid", uid)));
        scheduleIcon.setOnClickListener(v -> startActivity(new Intent(this, Students_Calendar.class).putExtra("uid", uid)));
        reportIcon.setOnClickListener(v -> startActivity(new Intent(this, Students_Report.class).putExtra("uid", uid)));

    }



    // SECTION: Announcement

    private void fetchAnnouncements() {
        Log.d(TAG, "Fetching announcements...");
        db.collection("announcements")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "Announcements fetched. Count: " + queryDocumentSnapshots.size());
                        noAnnounceTextView.setVisibility(View.GONE);
                        announcementLayout.removeAllViews();

                        for (DocumentSnapshot document : queryDocumentSnapshots) {
                            Announcement announcement = document.toObject(Announcement.class);
                            if (announcement != null) {
                                announcement.setId(document.getId());
                                addAnnouncementView(announcement);
                            }
                        }
                    } else {
                        noAnnounceTextView.setVisibility(View.VISIBLE);
                        Log.d(TAG, "No announcements available.");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching announcements: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error fetching announcements: " + e.getMessage());
                });
    }

    private void fetchValidRooms() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("rooms")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> roomCodes = new ArrayList<>();
                    Date currentTime = new Date(); // Current time to compare against the room's endTime

                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Timestamp endTimeTimestamp = document.getTimestamp("endTime");

                        if (endTimeTimestamp != null && endTimeTimestamp.toDate().after(currentTime)) {
                            roomCodes.add(document.getString("roomCode"));
                        }
                    }

                    Log.d("AnnouncementActivity", "Valid rooms: " + roomCodes); // Debug log

                    // Populate the Spinner with the valid room codes
                    setupRoomSpinner(spinner, roomCodes); // <-- the spinner you got from modalFormView

                })
                .addOnFailureListener(e -> {
                    Log.e("AnnouncementActivity", "Error fetching rooms: " + e.getMessage());
                });
    }

    private void setupRoomSpinner(Spinner spinner, List<String> roomCodes) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roomCodes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }


    // NOTE: adds the announcements into the list
    private void addAnnouncementView(Announcement announcement) {
        View cardView = getLayoutInflater().inflate(R.layout.announcement_list, null);

        TextView titleTextView = cardView.findViewById(R.id.title);
        TextView teacherTextView = cardView.findViewById(R.id.teacher);
        TextView timestampTextView = cardView.findViewById(R.id.timestamp);
        TextView roomCodeTextView = cardView.findViewById(R.id.roomCode);
        Button editButton = cardView.findViewById(R.id.editAnnouncement);
        Button deleteButton = cardView.findViewById(R.id.deleteAnnouncement);

        titleTextView.setText(announcement.getAnnouncement_title());
        teacherTextView.setText("By: " + announcement.getTeacherName());
        timestampTextView.setText(announcement.getFormattedTimestamp());
        roomCodeTextView.setText(announcement.getRoomCode());

        cardView.setOnClickListener(v -> showAnnouncementModal(announcement));

        if (userType.equals("Student")) {
            editButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
        } else {
            editButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, EditAnnouncementActivity.class);
                intent.putExtra("announcementId", announcement.getId());
                intent.putExtra("title", announcement.getAnnouncement_title());
                intent.putExtra("message", announcement.getAnnouncement_message());
                intent.putExtra("roomCode", announcement.getRoomCode());
                startActivity(intent);
            });

            deleteButton.setOnClickListener(v -> {
                db.collection("announcements").document(announcement.getId())
                        .delete()
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(this, "Announcement deleted", Toast.LENGTH_SHORT).show();
                            fetchAnnouncements(); // Refresh list
                        });
            });
        }

        announcementLayout.addView(cardView);
    }

    private void showAnnouncementModal(Announcement announcement) {
        FrameLayout modalContainer = findViewById(R.id.announcementViewContainer);
        modalContainer.setVisibility(View.VISIBLE);

        // Inflate modal layout
        View modalView = getLayoutInflater().inflate(R.layout.activity_announcement_view, modalContainer, false);
        modalContainer.removeAllViews(); // Remove previous instances
        modalContainer.addView(modalView);

        // Get references from modal layout
        TextView titleView = modalView.findViewById(R.id.announcementTitle);
        TextView messageView = modalView.findViewById(R.id.announcementMessage);
        Button closeButton = modalView.findViewById(R.id.cancelButton);

        // Set announcement details
        titleView.setText(announcement.getAnnouncement_title());
        messageView.setText(announcement.getAnnouncement_message());

        // Handle close button
        closeButton.setOnClickListener(v -> modalContainer.setVisibility(View.GONE));
    }

    private void setupUI() {
        ImageView homeIcon = findViewById(R.id.homeIcon);
        ImageView reportIcon = findViewById(R.id.reportIcon);
        ImageView scheduleIcon = findViewById(R.id.scheduleIcon);
        ImageView menuIcon = findViewById(R.id.menuIcon);
        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);

        //TODO: add the sidebar /?
        NavigationView navigationView = findViewById(R.id.navigationView);
        TextView navUsername = navigationView.findViewById(R.id.navUsername);
        TextView navIdNumber = navigationView.findViewById(R.id.navIdNumber);

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(AnnouncementActivity.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        homeIcon.setOnClickListener(v -> {
            Intent intent = new Intent(AnnouncementActivity.this, Students_Home.class);
            startActivity(intent);
        });

        reportIcon.setOnClickListener(v -> {
            Intent intent = new Intent(AnnouncementActivity.this, Students_Report.class);
            startActivity(intent);
        });

        scheduleIcon.setOnClickListener(v -> {
            Intent intent = new Intent(AnnouncementActivity.this, Students_Calendar.class);
            startActivity(intent);
        });

        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

    }

}
