package com.example.smarttrack;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Admins_Room extends AppCompatActivity {
    private String uid;
    private LinearLayout roomsLayout;
    private ImageView reportIcon;
    private ImageView homeIcon;
    private ImageView scheduleIcon;
    private TextView navUsername, navIdNumber;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private LinearLayout floatingWindow;
    private Button createRoomButton,scanQRButtons;
    private EditText searchField;
    private ImageView sortButton;
    private boolean isAscending = false;
    private boolean isAdmin = false;
  //  private ProgressBar progressBar;


    private Map<String, String> teacherNames = new HashMap<>();



    private Map<String, Map<String, String>> allRooms = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rooms);

        uid = getIntent().getStringExtra("uid");

        roomsLayout = findViewById(R.id.roomsLayout);
        floatingWindow = findViewById(R.id.floatingWindow);
        reportIcon = findViewById(R.id.reportIcon);
        homeIcon = findViewById(R.id.homeIcon);
        scheduleIcon = findViewById(R.id.scheduleIcon);
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navUsername = navigationView.findViewById(R.id.navUsername);
        navIdNumber = navigationView.findViewById(R.id.navIdNumber);
        createRoomButton = findViewById(R.id.createRoomButton);
        Button inputCodeButton = findViewById(R.id.inputCodeButton);
        TextView ORtextView = findViewById(R.id.ORtextView);
        searchField = findViewById(R.id.searchField);
        Button scanQRButtons = findViewById(R.id.scanQRButtons);
        TextView manualLink = findViewById(R.id.manualLink);

        ViewPager viewPager = findViewById(R.id.viewPager);
        setupViewPager(viewPager);

        sortButton = findViewById(R.id.sortButton);
        sortButton.setOnClickListener(v -> sortRooms());



        RoomsPagerAdapter adapter = new RoomsPagerAdapter(getSupportFragmentManager());





        inputCodeButton.setVisibility(View.GONE);
        ORtextView.setVisibility(View.GONE);
        scanQRButtons.setVisibility(View.GONE);


        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

      //  progressBar = findViewById(R.id.progressBar);

        // Show ProgressBar while fetching data
     //   progressBar.setVisibility(View.VISIBLE);

        fetchStudentDetails(uid);
        fetchTeacherNames();

        // Fetch rooms created by the teacher
        fetchAllRooms();

        // Set up actions for buttons (report, home, etc.)
        setupButtons();

        // Set OnClickListener for createRoomButton to redirect to Teachers_CreateRoom activity
        createRoomButton.setOnClickListener(v -> {
            Intent intent = new Intent(Admins_Room.this, Admins_CreateRoom.class);
            intent.putExtra("uid", uid);  // Pass the UID to Teachers_CreateRoom
            startActivity(intent);
        });

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterRooms(s.toString()); // Use the main filter method
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        manualLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://www.figma.com/proto/Wu25UR6CZCS31h2bFZXl6Y/SmartTrack_Manual?node-id=1-3&t=WKwQW6bIifbQtPB8-1&scaling=scale-down&content-scaling=fixed&page-id=0%3A1&starting-point-node-id=1%3A3";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        });




        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Admins_Room.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish(); // Ensure this is called properly
        });

// Sort button listener
        sortButton.setOnClickListener(v -> {
            sortRooms(); // Call sorting function
        });

    }

    // Method to fetch rooms created by the teacher
    private Map<String, Map<String, String>> originalRooms = new HashMap<>(); // Stores all rooms permanently

    private void fetchAllRooms() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("rooms")
                .get()
                .addOnSuccessListener(allRoomsSnapshot -> {
                    allRooms.clear();
                    originalRooms.clear();

                    if (allRoomsSnapshot != null && !allRoomsSnapshot.isEmpty()) {
                        for (QueryDocumentSnapshot document : allRoomsSnapshot) {
                            String adminId = document.getString("adminId");
                            String teacherId = document.getString("teacherId");
                            String creatorInfo;

                            if (adminId != null) {
                                if (adminId.equals(uid)) {
                                    creatorInfo = "Created by: You";
                                } else {
                                    creatorInfo = "Created by: Admin";
                                }
                            } else if (teacherId != null) {
                                if (teacherNames.containsKey(teacherId)) {
                                    creatorInfo = "Created by: " + teacherNames.get(teacherId);
                                } else {
                                    creatorInfo = "Created by: Unknown Teacher";
                                }
                            } else {
                                creatorInfo = "Created by: Unknown";
                            }


                            addRoomToMap(document, creatorInfo);
                        }
                    }

                    displayRooms(allRooms);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(Admins_Room.this, "Error fetching all rooms: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    /*    new Handler().postDelayed(() -> {
            // After fetching rooms
            progressBar.setVisibility(View.GONE);
        }, 2000);*/
    }



    private void addRoomToMap(QueryDocumentSnapshot document, String creatorInfo) {
        String subjectCode = document.getString("subjectCode") != null ? document.getString("subjectCode") : "Unknown";
        String section = document.getString("section") != null ? document.getString("section") : "Unknown";
        String roomCode = document.getString("roomCode") != null ? document.getString("roomCode") : "Unknown";
        String endDate = document.getString("endDate") != null ? document.getString("endDate") : "";

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
        detailsMap.put("creatorInfo", creatorInfo);
        detailsMap.put("endDate", endDate); // 🛠 Store endDate properly

        allRooms.put(roomCode, detailsMap);
        originalRooms.put(roomCode, detailsMap);
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

    public void displayRooms(Map<String, Map<String, String>> roomDetails) {
        roomsLayout.removeAllViews();

        if (!roomDetails.isEmpty()) {
            roomsLayout.setVisibility(View.VISIBLE);
            TextView noRoomsTextView = findViewById(R.id.noRoomsTextView);
            noRoomsTextView.setVisibility(View.GONE);

            for (Map.Entry<String, Map<String, String>> entry : roomDetails.entrySet()) { // No Sorting Here
                String roomCode = entry.getKey();
                Map<String, String> detailsMap = entry.getValue();

                String subjectCode = detailsMap.get("subjectCode");
                String section = detailsMap.get("section");
                String startTime = detailsMap.get("startTime");
                String endTime = detailsMap.get("endTime");
                String endDateStr = detailsMap.get("endDate");
                boolean isActive = Boolean.parseBoolean(detailsMap.get("isActive"));

                if (isRoomExpired(endDateStr)) {
                    Log.d("RoomFilter", "Skipping expired room: " + roomCode + " with endDate: " + endDateStr);
                    continue;
                }

                // Create CardView
                CardView cardView = new CardView(this);
                cardView.setRadius(20f);
                cardView.setCardElevation(10f);
                cardView.setUseCompatPadding(true);

                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                cardParams.setMargins(0, 0, 0, 20);
                cardView.setLayoutParams(cardParams);

                // Create RelativeLayout as the main container
                RelativeLayout cardLayout = new RelativeLayout(this);
                cardLayout.setPadding(30, 30, 30, 30);

                // Status Indicator
                View statusIndicator = new View(this);
                RelativeLayout.LayoutParams statusParams = new RelativeLayout.LayoutParams(30, 30);
                statusParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                statusParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                statusParams.setMargins(0, 20, 20, 0);
                statusIndicator.setLayoutParams(statusParams);
                statusIndicator.setBackgroundColor(isActive ? Color.GREEN : Color.RED);

                // Vertical Layout to Hold Icon + Details
                LinearLayout contentLayout = new LinearLayout(this);
                contentLayout.setOrientation(LinearLayout.VERTICAL);
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

                // Add views in the correct order
                contentLayout.addView(iconView);
                contentLayout.addView(detailsView);

                // Additional "Created by: " text with specific creator info
                String creatorInfo = detailsMap.get("creatorInfo");
                TextView createdByView = new TextView(this);
                createdByView.setText(creatorInfo);
                createdByView.setTextSize(16);
                createdByView.setTypeface(Typeface.DEFAULT);
                createdByView.setTextColor(Color.GRAY);
                contentLayout.addView(createdByView);

                cardLayout.addView(statusIndicator);
                cardLayout.addView(contentLayout);

                cardView.addView(cardLayout);

                // Set click listener
                cardView.setOnClickListener(v -> showFloatingWindow(roomCode, subjectCode, section));
                cardView.setOnLongClickListener(v -> {
                    showEditDeleteButtons(roomCode);
                    return true;
                });

                // Add CardView to roomsLayout
                roomsLayout.addView(cardView);
            }
        } else {
            TextView noRoomsTextView = findViewById(R.id.noRoomsTextView);
            noRoomsTextView.setVisibility(View.VISIBLE);
        }
    }


    // Helper method to check if a room is expired based on endDate
    private boolean isRoomExpired(String endDateStr) {
        if (endDateStr == null || endDateStr.isEmpty()) {
            return false; // If no end date, consider room as active
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date endDate = sdf.parse(endDateStr);
            if (endDate != null) {
                Date currentDate = new Date();
                boolean isExpired = currentDate.after(endDate);
                Log.d("DateCheck", "Checking if room is expired. End date: " + endDateStr + " | Current date: " + sdf.format(currentDate) + " | Expired: " + isExpired);
                return isExpired;
            }
        } catch (Exception e) {
            Log.e("DateParseError", "Failed to parse end date: " + endDateStr, e);
        }

        return false; // Default to not expired if parsing fails
    }

    // Main method for filtering rooms based on search query



    // Method to filter rooms based on room code, subject code, or section
    // Method to filter rooms based on room code, subject code, or section
    // Method to filter rooms based on room code, subject code, or section
    private Map<String, Map<String, String>> filterRoomsByQuery(String query) {
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
        return filteredRooms;
    }


    // Method to filter rooms by teacher first or last name
    private Map<String, Map<String, String>> filterRoomsByTeacherName(String query) {
        Map<String, Map<String, String>> filteredRooms = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : originalRooms.entrySet()) {
            Map<String, String> details = entry.getValue();
            String creatorInfo = details.get("creatorInfo") != null ? details.get("creatorInfo").toLowerCase() : "";

            for (Map.Entry<String, String> teacherEntry : teacherNames.entrySet()) {
                String teacherName = teacherEntry.getValue().toLowerCase();
                if (teacherName.contains(query) && creatorInfo.contains(teacherName)) {
                    filteredRooms.put(entry.getKey(), entry.getValue());
                    break; // Stop further searching once a match is found
                }
            }
        }
        return filteredRooms;
    }

    // Method to filter rooms by type (admin or teacher)
    private Map<String, Map<String, String>> filterRoomsByType(String roomType) {
        Map<String, Map<String, String>> filteredRooms = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : allRooms.entrySet()) {
            Map<String, String> detailsMap = entry.getValue();
            String creatorInfo = detailsMap.get("creatorInfo");

            boolean isTeacherRoom = creatorInfo != null && !creatorInfo.contains("Admin");

            if ((roomType.equals("teacher") && isTeacherRoom) ||
                    (roomType.equals("admin") && !isTeacherRoom)) {
                filteredRooms.put(entry.getKey(), entry.getValue());
            }
        }
        return filteredRooms;
    }


    private void sortRooms() {
        // Toggle sorting order
        isAscending = !isAscending;
        Log.d("SORT", "Sorting Order: " + (isAscending ? "Ascending" : "Descending"));

        // Convert map entries to a list for sorting
        List<Map.Entry<String, Map<String, String>>> sortedRooms = new ArrayList<>(allRooms.entrySet());

        // Sorting logic based on subjectCode
        sortedRooms.sort((entry1, entry2) -> {
            String subjectCode1 = entry1.getValue().get("subjectCode");
            String subjectCode2 = entry2.getValue().get("subjectCode");

            // Handle null values
            if (subjectCode1 == null) subjectCode1 = "";
            if (subjectCode2 == null) subjectCode2 = "";

            return isAscending
                    ? subjectCode1.compareToIgnoreCase(subjectCode2)
                    : subjectCode2.compareToIgnoreCase(subjectCode1);
        });

        // Create a NEW LinkedHashMap with sorted order
        Map<String, Map<String, String>> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : sortedRooms) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        // Update allRooms reference
        allRooms = sortedMap;

        // DEBUG: Log sorted order
        for (Map.Entry<String, Map<String, String>> entry : allRooms.entrySet()) {
            Log.d("SORT", "Room: " + entry.getKey() + ", Subject: " + entry.getValue().get("subjectCode"));
        }

        Log.d("SORT", "Sorted List Updated Successfully");

        // Refresh UI
        displayRooms(allRooms);
    }


    private void showEditDeleteButtons(String roomCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Set the title with maroon color
        SpannableString title = new SpannableString("What do you want to do?");
        title.setSpan(new ForegroundColorSpan(Color.parseColor("#800000")), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setTitle(title);

        // Create the dialog
        AlertDialog dialog = builder.create();

        // Set up the buttons
        builder.setPositiveButton("Edit", (dialogInterface, which) -> {
            FirebaseFirestore.getInstance().collection("rooms")
                    .whereEqualTo("roomCode", roomCode)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            String roomId = querySnapshot.getDocuments().get(0).getId();
                            Log.d("Admins_Room", "Room ID for Edit: " + roomId);

                            Intent intent = new Intent(Admins_Room.this, Teachers_EditRoom.class);
                            intent.putExtra("roomId", roomId);
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "Room not found", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error fetching room ID: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        builder.setNegativeButton("Delete", (dialogInterface, which) -> {
            deleteRoom(roomCode);
        });

        builder.setNeutralButton("Cancel", (dialogInterface, which) -> dialogInterface.dismiss());

        // Create and show the dialog
        dialog = builder.create();
        dialog.show();

        // Set Maroon color for buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#800000"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#800000"));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.GRAY); // Keeping Cancel button gray
    }


    private void deleteRoom(String roomCode) {
        FirebaseFirestore.getInstance().collection("rooms")
                .whereEqualTo("roomCode", roomCode)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        String roomId = querySnapshot.getDocuments().get(0).getId();
                        FirebaseFirestore.getInstance().collection("rooms")
                                .document(roomId)
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Room deleted successfully", Toast.LENGTH_SHORT).show();
                                    fetchAllRooms(); // Refresh rooms list
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Error deleting room: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        Toast.makeText(this, "Room not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching room: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }





    // Method to show the floating window with buttons
    private void showFloatingWindow(String roomCode, String subjectCode, String section) {
        View blurBackground = findViewById(R.id.blurBackground);
        floatingWindow.setVisibility(View.VISIBLE);  // Show the floating window
        blurBackground.setVisibility(View.VISIBLE);  // Show the blur background
        roomsLayout.setVisibility(View.GONE);

        // Set room-related text
        Button generateCodeButton = findViewById(R.id.generateCodeButton);
        Button viewStudentsButton = findViewById(R.id.viewStudentsButton);
        generateCodeButton.setText("Generate Code");
        viewStudentsButton.setText("View Students");



        // Set onClickListeners for the buttons inside the floating window
        generateCodeButton.setOnClickListener(v -> {
            Intent intent = new Intent(Admins_Room.this, GenerateCode.class);
            intent.putExtra("roomCode", roomCode); // Pass the roomCode to GenerateCode
            intent.putExtra("subjectSection", subjectCode + " - " + section); // Include subject and section
            startActivity(intent);
        });

        viewStudentsButton.setOnClickListener(v -> {
            if (roomCode == null || roomCode.isEmpty()) {
                Toast.makeText(this, "Room Code is missing. Cannot view students.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Fetch the roomId based on the roomCode before transitioning
            FirebaseFirestore.getInstance().collection("rooms")
                    .whereEqualTo("roomCode", roomCode)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            String roomId = queryDocumentSnapshots.getDocuments().get(0).getId();
                            Log.d("showFloatingWindow", "Room ID resolved: " + roomId);

                            Intent intent = new Intent(Admins_Room.this, ViewStudents.class);
                            intent.putExtra("roomId", roomId); // Pass the resolved roomId to ViewStudents
                            intent.putExtra("section", section); // Pass the section to ViewStudents
                            intent.putExtra("subjectCode", subjectCode); // Pass the subjectCode to ViewStudents
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "No room found for the provided code.", Toast.LENGTH_SHORT).show();
                            Log.e("showFloatingWindow", "No room found for roomCode: " + roomCode);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error fetching room ID: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e("showFloatingWindow", "Error fetching room ID for roomCode: " + roomCode, e);
                    });
        });

        // Close button for the floating window
        ImageView closeFloatingWindow = findViewById(R.id.closeFloatingWindow);
        closeFloatingWindow.setOnClickListener(v -> {
            floatingWindow.setVisibility(View.GONE);  // Hide the floating window
            blurBackground.setVisibility(View.GONE);  // Hide the blur background
            roomsLayout.setVisibility(View.VISIBLE);  // Show the rooms layout again
        });
    }



    // Fetch teacher details (name, idNumber, etc.)
    private void fetchStudentDetails(String uid) {
        FirebaseFirestore.getInstance().collection("administrator")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        isAdmin = true; // Set admin flag if the document exists
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        String idNumber = document.getString("idNumber");
                        navUsername.setText(firstName + " " + lastName);
                        navIdNumber.setText(idNumber);
                        Log.d("AdminCheck", "User is an admin");
                    } else {
                        isAdmin = false;
                        Log.d("AdminCheck", "User is NOT an admin");
                    }
                    fetchAllRooms(); // Ensure rooms are fetched after isAdmin is set
                })
                .addOnFailureListener(e -> {
                    navUsername.setText("Error fetching details");
                    navIdNumber.setText("");
                    isAdmin = false;
                    Log.e("AdminCheck", "Failed to fetch admin status: " + e.getMessage());
                });

       /* new Handler().postDelayed(() -> {
            // After fetching data
            progressBar.setVisibility(View.GONE);
        }, 2000); */
    }



    private void setupButtons() {
        reportIcon.setOnClickListener(v -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Report");
            }
            Intent intent = new Intent(Admins_Room.this, Admins_Report.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        homeIcon.setOnClickListener(v -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Home");
            }
            Intent intent = new Intent(Admins_Room.this, Admins_Home.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        scheduleIcon.setOnClickListener(v -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Calendar");
            }
            Intent intent = new Intent(Admins_Room.this, Admins_Calendar.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
    }
    private void fetchTeacherNames() {
        FirebaseFirestore.getInstance().collection("teachers")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String teacherId = document.getId();
                            String firstName = document.getString("firstName");
                            String lastName = document.getString("lastName");
                            if (firstName != null && lastName != null) {
                                teacherNames.put(teacherId, firstName + " " + lastName);
                            }
                        }
                    }
                    fetchAllRooms(); // Fetch rooms only after loading teacher names
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching teacher names: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

      /*  new Handler().postDelayed(() -> {
            // After fetching teacher names
            progressBar.setVisibility(View.GONE);
        }, 2000);*/

    }

    private void setupViewPager(ViewPager viewPager) {
        RoomsPagerAdapter adapter = new RoomsPagerAdapter(getSupportFragmentManager());

        // Create fragments for each tab

        Map<String, Map<String, String>> adminRooms = filterRoomsByType("admin");
        Map<String, Map<String, String>> teacherRooms = filterRoomsByType("teacher");

        adapter.addFragment(RoomsFragment.newInstance("admin", adminRooms), "Admin Rooms");
        adapter.addFragment(RoomsFragment.newInstance("teacher", teacherRooms), "Teacher Rooms");

        viewPager.setAdapter(adapter);
    }


    private void filterRooms(String query) {
        query = query.trim().toLowerCase();

        if (query.isEmpty()) {
            // Restore all rooms from originalRooms
            allRooms.clear();
            allRooms.putAll(originalRooms);
            displayRooms(allRooms);
            return;
        }

        // Filter rooms based on subject code, section, room code, or teacher's first/last name
        Map<String, Map<String, String>> filteredRooms = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : originalRooms.entrySet()) {
            Map<String, String> details = entry.getValue();
            String subjectCode = details.get("subjectCode") != null ? details.get("subjectCode").toLowerCase() : "";
            String section = details.get("section") != null ? details.get("section").toLowerCase() : "";
            String roomCode = entry.getKey().toLowerCase();
            String creatorInfo = details.get("creatorInfo") != null ? details.get("creatorInfo").toLowerCase() : "";

            // Check if the query matches subjectCode, section, roomCode, or teacher name
            if (subjectCode.contains(query) || section.contains(query) || roomCode.contains(query) || creatorInfo.contains(query)) {
                filteredRooms.put(entry.getKey(), entry.getValue());
            } else {
                // Additionally check if the query matches the first name or last name of any teacher
                for (Map.Entry<String, String> teacherEntry : teacherNames.entrySet()) {
                    String teacherName = teacherEntry.getValue().toLowerCase();
                    if (teacherName.contains(query)) {
                        String teacherId = teacherEntry.getKey();
                        // Check if the room was created by this teacher
                        if (creatorInfo.contains(teacherName)) {
                            filteredRooms.put(entry.getKey(), entry.getValue());
                            break; // No need to check other teachers if a match is found
                        }
                    }
                }
            }
        }

        // Display filtered rooms with highlighted text
        if (!filteredRooms.isEmpty()) {
            displayRooms(filteredRooms);
        } else {
            roomsLayout.removeAllViews();
            findViewById(R.id.noRoomsTextView).setVisibility(View.VISIBLE); // Show "No Rooms Found"
        }
    }
}


