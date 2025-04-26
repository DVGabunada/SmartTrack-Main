package com.example.smarttrack;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class Teachers_Room extends AppCompatActivity {

    private String uid;
    private LinearLayout roomsLayout;
    private ImageView reportIcon;
    private ImageView homeIcon;
    private ImageView scheduleIcon;
    private TextView navUsername, navIdNumber;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private LinearLayout floatingWindow;
    private Button createRoomButton;

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

        inputCodeButton.setVisibility(View.GONE);
        ORtextView.setVisibility(View.GONE);


        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        fetchStudentDetails(uid);

        // Fetch rooms created by the teacher
        fetchRoomsByTeacher(uid);

        // Set up actions for buttons (report, home, etc.)
        setupButtons();

        // Set OnClickListener for createRoomButton to redirect to Teachers_CreateRoom activity
        createRoomButton.setOnClickListener(v -> {
            Intent intent = new Intent(Teachers_Room.this, Teachers_CreateRoom.class);
            intent.putExtra("uid", uid);  // Pass the UID to Teachers_CreateRoom
            startActivity(intent);
        });
    }

    // Method to fetch rooms created by the teacher
    private void fetchRoomsByTeacher(String uid) {
        FirebaseFirestore.getInstance().collection("rooms")
                .whereEqualTo("teacherId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, String> roomDetails = new HashMap<>();
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String subjectCode = document.getString("subjectCode");
                            String section = document.getString("section");
                            String roomCode = document.getString("roomCode");

                            String displayText = subjectCode + " - " + section;
                            roomDetails.put(displayText, roomCode);
                        }
                        displayRooms(roomDetails);
                    } else {
                        Toast.makeText(Teachers_Room.this, "No rooms found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Teachers_Room.this, "Error fetching rooms: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }





    // Method to display rooms as buttons
    private void displayRooms(Map<String, String> roomDetails) {
        roomsLayout.removeAllViews();  // Clear existing views

        if (!roomDetails.isEmpty()) {
            roomsLayout.setVisibility(View.VISIBLE);
            TextView noRoomsTextView = findViewById(R.id.noRoomsTextView);
            noRoomsTextView.setVisibility(View.GONE);

            for (Map.Entry<String, String> entry : roomDetails.entrySet()) {
                String displayText = entry.getKey();
                String roomCode = entry.getValue();
                String[] parts = displayText.split(" - ");
                String subjectCode = parts[0];
                String section = parts.length > 1 ? parts[1] : "";

                // Create Room Button
                Button roomButton = new Button(this);
                roomButton.setText(displayText);
                roomButton.setTextSize(25);
                roomButton.setPadding(20, 20, 20, 20);
                roomButton.setTextColor(getResources().getColor(android.R.color.black));
                roomButton.setBackground(getResources().getDrawable(R.drawable.button_border));

                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                layoutParams.setMargins(0, 10, 0, 10);
                roomButton.setLayoutParams(layoutParams);

                // ✅ Handle Normal Click - Show Floating Window
                roomButton.setOnClickListener(v -> {
                    showFloatingWindow(roomCode, subjectCode, section);
                });

                // ✅ Handle Long Press - Show Edit/Delete Buttons
                roomButton.setOnLongClickListener(v -> {
                    showEditDeleteButtons(roomButton, roomCode);
                    return true; // Consume the long-press event
                });

                roomsLayout.addView(roomButton);
            }
        } else {
            TextView noRoomsTextView = findViewById(R.id.noRoomsTextView);
            noRoomsTextView.setVisibility(View.VISIBLE);
        }
    }

    private void showEditDeleteButtons(Button roomButton, String roomCode) {
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
                            Log.d("Teachers_Room", "Room ID for Edit: " + roomId);

                            Intent intent = new Intent(Teachers_Room.this, Teachers_EditRoom.class);
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
                                    fetchRoomsByTeacher(uid); // Refresh rooms list
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






    private void showFloatingWindow(String roomCode, String subjectCode, String section) {
        View blurBackground = findViewById(R.id.blurBackground);
        floatingWindow.setVisibility(View.VISIBLE);
        blurBackground.setVisibility(View.VISIBLE);
        roomsLayout.setVisibility(View.GONE);


        Button generateCodeButton = findViewById(R.id.generateCodeButton);
        Button viewStudentsButton = findViewById(R.id.viewStudentsButton);
        generateCodeButton.setText("Generate Code");
        viewStudentsButton.setText("View Students");




        generateCodeButton.setOnClickListener(v -> {
            Intent intent = new Intent(Teachers_Room.this, GenerateCode.class);
            intent.putExtra("roomCode", roomCode);
            intent.putExtra("subjectSection", subjectCode + " - " + section); // Include subject and section
            startActivity(intent);
        });

        viewStudentsButton.setOnClickListener(v -> {
            if (roomCode == null || roomCode.isEmpty()) {
                Toast.makeText(this, "Room Code is missing. Cannot view students.", Toast.LENGTH_SHORT).show();
                return;
            }


            FirebaseFirestore.getInstance().collection("rooms")
                    .whereEqualTo("roomCode", roomCode)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            String roomId = queryDocumentSnapshots.getDocuments().get(0).getId();
                            Log.d("showFloatingWindow", "Room ID resolved: " + roomId);

                            Intent intent = new Intent(Teachers_Room.this, ViewStudents.class);
                            intent.putExtra("roomId", roomId);
                            intent.putExtra("section", section);
                            intent.putExtra("subjectCode", subjectCode);
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
        FirebaseFirestore.getInstance().collection("teachers")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        String idNumber = document.getString("idNumber");
                        navUsername.setText(firstName + " " + lastName);
                        navIdNumber.setText(idNumber);
                    }
                })
                .addOnFailureListener(e -> {
                    navUsername.setText("Error fetching details");
                    navIdNumber.setText("");
                });
    }

    private void setupButtons() {
        reportIcon.setOnClickListener(v -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Report");
            }
            Intent intent = new Intent(Teachers_Room.this, Teachers_Report.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        homeIcon.setOnClickListener(v -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Home");
            }
            Intent intent = new Intent(Teachers_Room.this, Teachers_Home.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        scheduleIcon.setOnClickListener(v -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Calendar");
            }
            Intent intent = new Intent(Teachers_Room.this, Teachers_Calendar.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
    }
}