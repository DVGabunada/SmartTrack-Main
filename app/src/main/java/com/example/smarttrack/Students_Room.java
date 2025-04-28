package com.example.smarttrack;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Students_Room extends AppCompatActivity {

    private static final String TAG = "Students_Room";

    private ImageView reportIcon;
    private ImageView homeIcon;
    private ImageView scheduleIcon;
    private String uid;
    private Button scanQRButton, inputCodeButton, createRoomButton;
    private TextView noRoomsTextView;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView navUsername, navIdNumber;
    private LinearLayout roomsLayout;
    private EditText searchField;
    private ImageView sortButton;
    private boolean isAscending = false;
    private Map<String, Map<String, String>> allRooms = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rooms);

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("Rooms");

        // Retrieve data from the Intent
        String roomId = getIntent().getStringExtra("roomId");
        String teacherId = getIntent().getStringExtra("teacherId");
        String section = getIntent().getStringExtra("section");
        String subjectCode = getIntent().getStringExtra("subjectCode");
        String studentId = getIntent().getStringExtra("studentId");

        // Initialize uid
        if (studentId != null) {
            uid = studentId;
        } else {
            uid = FirebaseAuth.getInstance().getUid(); // Fallback to logged-in user
        }

        // Validate data
        if (uid == null) {
            Toast.makeText(this, "Student ID is missing. Please log in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Log the received data
        Log.d(TAG, "Room ID: " + roomId);
        Log.d(TAG, "Teacher ID: " + teacherId);
        Log.d(TAG, "Section: " + section);
        Log.d(TAG, "Subject Code: " + subjectCode);
        Log.d(TAG, "Student ID: " + uid);

        noRoomsTextView = findViewById(R.id.noRoomsTextView);
        roomsLayout = findViewById(R.id.roomsLayout);

        // Setup Views
        setupUI();
        fetchStudentDetailed(uid);
        fetchRooms();

        searchField = findViewById(R.id.searchField);
        sortButton = findViewById(R.id.sortButton);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterRooms(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

// Sort button listener
        sortButton.setOnClickListener(v -> {
            sortRooms(); // Call sorting function
        });
    }

    private Map<String, Map<String, String>> originalRooms = new HashMap<>();

    private void setupUI() {
        reportIcon = findViewById(R.id.reportIcon);
        homeIcon = findViewById(R.id.homeIcon);
        scheduleIcon = findViewById(R.id.scheduleIcon);
        scanQRButton = findViewById(R.id.scanQRButtons);
        inputCodeButton = findViewById(R.id.inputCodeButton);

        // Drawer setup
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navUsername = navigationView.findViewById(R.id.navUsername);
        navIdNumber = navigationView.findViewById(R.id.navIdNumber);
        createRoomButton = findViewById(R.id.createRoomButton);

        TextView manualLink = findViewById(R.id.manualLink);
        createRoomButton.setVisibility(View.GONE);


        manualLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://www.figma.com/proto/Wu25UR6CZCS31h2bFZXl6Y/SmartTrack_Manual?node-id=107-571&t=fQwvahMuKzBnqHEf-1&scaling=min-zoom&content-scaling=fixed&page-id=107%3A570";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        });


        inputCodeButton.setOnClickListener(v -> {
            Intent intent = new Intent(Students_Room.this, InputCodeActivity.class);
            startActivity(intent);
        });

// Set click listener for scanQRButton
        scanQRButton.setOnClickListener(v -> {
            Intent intent = new Intent(Students_Room.this, ScanQRActivity.class);
            startActivity(intent);
        });



        reportIcon.setOnClickListener(v -> {
            getSupportActionBar().setTitle("Room");
            Intent intent = new Intent(Students_Room.this, Students_Report.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        // Set click listener for homeIcon
        homeIcon.setOnClickListener(v -> {
            getSupportActionBar().setTitle("Home");
            Intent intent = new Intent(Students_Room.this, Students_Home.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        // Set click listener for scheduleIcon
        scheduleIcon.setOnClickListener(v -> {
            getSupportActionBar().setTitle("Calendar");
            Intent intent = new Intent(Students_Room.this, Students_Calendar.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Students_Room.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish(); // Ensure this is called properly
        });

        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    }

    private void fetchStudentDetailed(String uid) {
        Log.d(TAG, "Fetching student details for UID: " + uid);
        FirebaseFirestore.getInstance().collection("students")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        String idNumber = document.getString("idNumber");
                        navUsername.setText(firstName + " " + lastName);
                        navIdNumber.setText(idNumber);
                        Log.d(TAG, "Student details fetched: " + firstName + " " + lastName);
                    } else {
                        Log.d(TAG, "Student document does not exist.");
                        navUsername.setText("Unknown User");
                        navIdNumber.setText("N/A");
                    }
                })
                .addOnFailureListener(e -> {
                    navUsername.setText("Error fetching details");
                    navIdNumber.setText("N/A");
                    Log.e(TAG, "Error fetching student details: " + e.getMessage());
                });
    }

    private void fetchRooms() {
        Log.d(TAG, "Fetching rooms...");
        FirebaseFirestore.getInstance().collection("rooms")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "Rooms fetched successfully. Count: " + queryDocumentSnapshots.size());

                        // Clear maps before adding new rooms
                        originalRooms.clear();
                        allRooms.clear();

                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String roomId = document.getId();

                            String subjectCode = document.getString("subjectCode") != null ? document.getString("subjectCode") : "Unknown";
                            String section = document.getString("section") != null ? document.getString("section") : "Unknown";
                            String endDate = document.getString("endDate");

                            Timestamp startTimeStamp = document.getTimestamp("startTime");
                            Timestamp endTimeStamp = document.getTimestamp("endTime");

                            String startTime = formatTimestamp(startTimeStamp);
                            String endTime = formatTimestamp(endTimeStamp);

                            boolean isActive = checkIfActive(endDate);

                            Map<String, String> detailsMap = new HashMap<>();
                            detailsMap.put("subjectCode", subjectCode);
                            detailsMap.put("section", section);
                            detailsMap.put("startTime", startTime);
                            detailsMap.put("endTime", endTime);
                            detailsMap.put("isActive", String.valueOf(isActive));

                            // First, check if the student is in the room before adding it
                            checkStudentInRoom(roomId, detailsMap);
                        }
                    } else {
                        noRoomsTextView.setVisibility(View.VISIBLE);
                        Log.d(TAG, "No rooms available.");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching rooms: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error fetching rooms: " + e.getMessage());
                });
    }


    private boolean checkIfActive(String endDate) {
        if (endDate == null || endDate.isEmpty()) {
            return false;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date end = sdf.parse(endDate);
            return end != null && new Date().before(end);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp != null) {
            Date date = timestamp.toDate();
            return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date); // Convert to "7:30 AM"
        }
        return "Unknown Time"; // Default value if timestamp is null
    }


    private void checkStudentInRoom(String roomId, Map<String, String> roomDetails) {
        Log.d(TAG, "Checking if student is part of room: " + roomId);

        FirebaseFirestore.getInstance()
                .collection("rooms")
                .document(roomId)
                .collection("students")
                .document(uid)
                .get()
                .addOnSuccessListener(studentDoc -> {
                    if (studentDoc.exists()) {
                        Log.d(TAG, "Student is part of room: " + roomId);

                        // Now add to allRooms and originalRooms
                        originalRooms.put(roomId, roomDetails);
                        allRooms.put(roomId, roomDetails);

                        // Update UI
                        createRoomCard(allRooms);
                    } else {
                        Log.d(TAG, "Student not part of room: " + roomId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking student in room: " + roomId, e);
                });
    }


    private void createRoomCard(Map<String, Map<String, String>> rooms) {
        roomsLayout.removeAllViews(); // Clear previous UI
        boolean hasActiveRooms = false; // Flag to check if there are any active rooms

        if (rooms.isEmpty()) {
            noRoomsTextView.setVisibility(View.VISIBLE); // Show "No Rooms Found"
            roomsLayout.setVisibility(View.GONE);
            return;
        } else {
            noRoomsTextView.setVisibility(View.GONE);
            roomsLayout.setVisibility(View.VISIBLE);
        }

        for (Map.Entry<String, Map<String, String>> entry : rooms.entrySet()) {
            String roomId = entry.getKey();
            Map<String, String> details = entry.getValue();

            String subjectCode = details.get("subjectCode");
            String section = details.get("section");
            String startTime = details.get("startTime");
            String endTime = details.get("endTime");
            boolean isActive = Boolean.parseBoolean(details.get("isActive"));

            // Skip rendering the card if the room is not active (endDate already passed)
            if (!isActive) {
                continue;
            }

            hasActiveRooms = true; // Mark that there's at least one active room

            Log.d(TAG, "Creating card for room: " + subjectCode + " - " + section);

            // Create CardView
            CardView cardView = new CardView(this);
            cardView.setRadius(20f);
            cardView.setCardElevation(10f);
            cardView.setUseCompatPadding(true);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 20, 0, 20);
            cardView.setLayoutParams(cardParams);

            RelativeLayout cardLayout = new RelativeLayout(this);
            cardLayout.setPadding(30, 30, 30, 30);

            // Status Indicator (Green for active, Red for inactive) - Top-right
            View statusIndicator = new View(this);
            RelativeLayout.LayoutParams statusParams = new RelativeLayout.LayoutParams(30, 30);
            statusParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            statusParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            statusParams.setMargins(0, 0, 20, 0);
            statusIndicator.setLayoutParams(statusParams);
            statusIndicator.setBackgroundColor(isActive ? Color.GREEN : Color.RED);

            // Horizontal Layout to Hold Icon + Details
            LinearLayout contentLayout = new LinearLayout(this);
            contentLayout.setOrientation(LinearLayout.HORIZONTAL);
            contentLayout.setGravity(Gravity.CENTER_VERTICAL);
            RelativeLayout.LayoutParams contentParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
            );
            contentParams.addRule(RelativeLayout.CENTER_VERTICAL);
            contentLayout.setLayoutParams(contentParams);

            // Icon (first letter of subject)
            TextView iconView = new TextView(this);
            iconView.setText(String.valueOf(subjectCode.charAt(0)).toUpperCase());
            iconView.setTextSize(30);
            iconView.setTypeface(Typeface.DEFAULT_BOLD);
            iconView.setGravity(Gravity.CENTER);
            iconView.setTextColor(Color.WHITE);
            iconView.setBackgroundResource(R.drawable.rounded_black_bg);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(120, 120);
            iconParams.setMargins(0, 0, 40, 0);
            iconView.setLayoutParams(iconParams);

            // Details (subject, section, times)
            TextView detailsView = new TextView(this);
            String detailsText = subjectCode + "\n" + section + "\n" + startTime + " - " + endTime;
            detailsView.setText(detailsText);
            detailsView.setTextSize(18);
            detailsView.setTypeface(Typeface.DEFAULT_BOLD);
            detailsView.setTextColor(Color.BLACK);

            // Add views
            contentLayout.addView(iconView);
            contentLayout.addView(detailsView);
            cardLayout.addView(statusIndicator);
            cardLayout.addView(contentLayout);

            cardView.addView(cardLayout);

            // Click event
            cardView.setOnClickListener(v -> {
                Intent intent = new Intent(Students_Room.this, StudentView.class);
                intent.putExtra("roomId", roomId);
                startActivity(intent);
            });

            runOnUiThread(() -> {
                roomsLayout.addView(cardView);
                Log.d(TAG, "Card added to UI. Total children: " + roomsLayout.getChildCount());
            });
        }

        // Check if no active rooms were displayed and show "No Rooms Found" message if needed
        if (!hasActiveRooms) {
            noRoomsTextView.setVisibility(View.VISIBLE);
            roomsLayout.setVisibility(View.GONE);
        }
    }



    private void filterRooms(String query) {
        query = query.trim().toLowerCase();

        if (query.isEmpty()) {
            // Restore all rooms from originalRooms
            allRooms.clear();
            allRooms.putAll(originalRooms);
            createRoomCard(allRooms);
            return;
        }

        // Filter rooms based on subject code, section, or room code
        Map<String, Map<String, String>> filteredRooms = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : originalRooms.entrySet()) {
            Map<String, String> details = entry.getValue();
            String subjectCode = details.get("subjectCode") != null ? details.get("subjectCode").toLowerCase() : "";
            String section = details.get("section") != null ? details.get("section").toLowerCase() : "";
            String roomCode = entry.getKey().toLowerCase();

            // Check if the query matches subjectCode, section, or roomCode
            if (subjectCode.contains(query) || section.contains(query) || roomCode.contains(query)) {
                filteredRooms.put(entry.getKey(), entry.getValue());
            }
        }

        // Display filtered rooms or show "No Rooms Found"
        if (!filteredRooms.isEmpty()) {
            createRoomCard(filteredRooms);
        } else {
            roomsLayout.removeAllViews();
            findViewById(R.id.noRoomsTextView).setVisibility(View.VISIBLE); // Show "No Rooms Found"
        }
    }


    private void sortRooms() {
        // Toggle sorting order
        isAscending = !isAscending;
        Log.d("SORT", "Sorting Order: " + (isAscending ? "Ascending" : "Descending"));

        // Convert map keys (roomCodes) to a list for sorting
        List<String> sortedKeys = new ArrayList<>(allRooms.keySet());

        // Sorting logic based on subjectCode
        sortedKeys.sort((key1, key2) -> {
            String subjectCode1 = allRooms.get(key1).get("subjectCode");
            String subjectCode2 = allRooms.get(key2).get("subjectCode");

            // Handle null values
            if (subjectCode1 == null) subjectCode1 = "";
            if (subjectCode2 == null) subjectCode2 = "";

            return isAscending ? subjectCode1.compareToIgnoreCase(subjectCode2)
                    : subjectCode2.compareToIgnoreCase(subjectCode1);
        });

        // Create a NEW LinkedHashMap with sorted order
        Map<String, Map<String, String>> sortedMap = new LinkedHashMap<>();
        for (String key : sortedKeys) {
            sortedMap.put(key, allRooms.get(key));
        }

        // Replace allRooms reference with the new sorted map
        allRooms = sortedMap;

        // DEBUG: Log sorted order
        for (Map.Entry<String, Map<String, String>> entry : allRooms.entrySet()) {
            Log.d("SORT", "Room: " + entry.getKey() + ", Subject: " + entry.getValue().get("subjectCode"));
        }

        Log.d("SORT", "Sorted List Updated Successfully");

        // Refresh UI
        roomsLayout.removeAllViews(); // Ensure previous views are removed
        createRoomCard(allRooms); // Force UI refresh
    }
}