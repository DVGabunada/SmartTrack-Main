package com.example.smarttrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Teachers_Home extends AppCompatActivity {

    private ImageView roomIcon;
    private ImageView reportIcon;
    private ImageView scheduleIcon;
    private TextView dashboardMessage;
    private TextView locationTextView;
    private LocationManager locationManager;
    private boolean locationDisplayed = false;
    private DrawerLayout drawerLayout;
    private LinearLayout floatingWindow;
    private NavigationView navigationView;
    private TextView navUsername, navIdNumber;
    private LinearLayout roomsLayout;
    private Button faceRegisterButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("Home");

        faceRegisterButton = findViewById(R.id.faceRegisterButton);
        faceRegisterButton.setVisibility(View.GONE);

        dashboardMessage = findViewById(R.id.dashboardMessage);
        locationTextView = findViewById(R.id.locationTextView);
        floatingWindow = findViewById(R.id.floatingWindow);
        roomsLayout = findViewById(R.id.roomsLayout);

        roomIcon = findViewById(R.id.roomIcon);
        scheduleIcon = findViewById(R.id.scheduleIcon);
        reportIcon = findViewById(R.id.reportIcon);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navUsername = navigationView.findViewById(R.id.navUsername);
        navIdNumber = navigationView.findViewById(R.id.navIdNumber);

        roomIcon.setClickable(true);
        scheduleIcon.setClickable(true);
        reportIcon.setClickable(true);


        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));


        String uid = getIntent().getStringExtra("uid");
        fetchRoomsByTeacher(uid);
        fetchUserDetailed(uid);

        ImageView announcementIcon = findViewById(R.id.announcementIcon);

        announcementIcon.setOnClickListener(v -> {
            Intent intent = new Intent(Teachers_Home.this, AnnouncementActivity.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });



        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Teachers_Home.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });





        roomIcon.setOnClickListener(v -> {
            getSupportActionBar().setTitle("Room");
            Intent intent = new Intent(Teachers_Home.this, Teachers_Room.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        scheduleIcon.setOnClickListener(v -> {
            getSupportActionBar().setTitle("Calendar");
            Intent intent = new Intent(Teachers_Home.this, Teachers_Calendar.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        reportIcon.setOnClickListener(v -> {
            getSupportActionBar().setTitle("Home");
            Intent intent = new Intent(Teachers_Home.this, Teachers_Report.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        fetchUserDetails(uid);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        requestLocationPermission();
    }


    private void fetchUserDetails(String uid) {
        FirebaseFirestore.getInstance().collection("teachers")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        dashboardMessage.setText("Hi, Teacher " + firstName + " " + lastName + "!");
                    } else {
                        dashboardMessage.setText("Teacher details not found.");
                    }
                })
                .addOnFailureListener(e -> dashboardMessage.setText("Error fetching teacher details."));
    }

    private void fetchUserDetailed(String uid) {
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

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            getCurrentLocation();
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationTextView.setText("Scanning your location...");
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    if (!locationDisplayed) {
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();
                        getAddressFromLocation(latitude, longitude);
                        locationDisplayed = true;
                    }
                }

                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            });
        }
    }

    private void getAddressFromLocation(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (!addresses.isEmpty()) {
                Address address = addresses.get(0);
                String fullAddress = address.getAddressLine(0);
                locationTextView.setText("Location: " + fullAddress);
            } else {
                locationTextView.setText("Unable to fetch address.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            locationTextView.setText("Error fetching address.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        } else {
            locationTextView.setText("Permission denied. Cannot fetch location.");
        }
    }

    private void fetchRoomsByTeacher(String uid) {
        String today = getTodayDayName(); // Get today's name (e.g., "Tuesday")

        FirebaseFirestore.getInstance().collection("rooms")
                .whereEqualTo("teacherId", uid) // 🔥 Only fetch rooms where the teacher is assigned
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, String> roomDetails = new HashMap<>();
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String roomId = document.getId();
                            String subjectCode = document.getString("subjectCode");
                            String section = document.getString("section");
                            List<String> schedule = (List<String>) document.get("schedule"); // Get schedule array

                            // ✅ Only show rooms scheduled for today
                            if (schedule != null && schedule.contains(today) && subjectCode != null && section != null) {
                                String displayText = subjectCode + " - " + section;
                                roomDetails.put(displayText, roomId);
                            }
                        }

                        displayRooms(roomDetails); // Update UI
                    } else {
                        Toast.makeText(Teachers_Home.this, "No rooms scheduled for today.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Teachers_Home.this, "Error fetching rooms: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String getTodayDayName() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    // Method to display rooms as buttons
    private void displayRooms(Map<String, String> roomDetails) {
        roomsLayout.removeAllViews();  // Clear any existing views in the layout

        if (!roomDetails.isEmpty()) {
            roomsLayout.setVisibility(View.VISIBLE);
            TextView noRoomsTextView = findViewById(R.id.noRoomsTextView);
            noRoomsTextView.setVisibility(View.GONE);

            for (Map.Entry<String, String> entry : roomDetails.entrySet()) {
                String displayText = entry.getKey();
                String roomID = entry.getValue();
                // Assuming the display text includes both subjectCode and section in the format "subjectCode - section"
                String[] parts = displayText.split(" - ");
                String subjectCode = parts[0];
                String section = parts.length > 1 ? parts[1] : "";  // Check if there is a section available

                Button roomButton = new Button(this);
                roomButton.setText(displayText);
                roomButton.setTextSize(25);
                roomButton.setPadding(20, 20, 20, 20);
                roomButton.setTextColor(getResources().getColor(android.R.color.black));
                roomButton.setBackground(getResources().getDrawable(R.drawable.button_border));

                float density = getResources().getDisplayMetrics().density;
                int marginBottom = (int) (10 * density);

                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                layoutParams.setMargins(0, 0, 0, marginBottom);
                roomButton.setLayoutParams(layoutParams);

                roomButton.setOnClickListener(v -> {
                    // Show the floating window with the room code, subject code, and section
                    showFloatingWindow(roomID, subjectCode, section);
                });

                roomsLayout.addView(roomButton);
            }
        } else {
            TextView noRoomsTextView = findViewById(R.id.noRoomsTextView);
            noRoomsTextView.setVisibility(View.VISIBLE);  // Show "No Rooms Available" message if list is empty
        }
    }

    private void showFloatingWindow(String roomID, String subjectCode, String section) {
        View blurBackground = findViewById(R.id.blurBackground);
        floatingWindow.setVisibility(View.VISIBLE);
        blurBackground.setVisibility(View.VISIBLE);
        roomsLayout.setVisibility(View.GONE);
        roomsLayout.setVisibility(View.GONE);

        // Initialize the buttons
        Button generateCodeButton = findViewById(R.id.generateCodeButton);
        Button viewStudentsButton = findViewById(R.id.viewStudentsButton);

        generateCodeButton.setVisibility(View.VISIBLE);
        viewStudentsButton.setVisibility(View.VISIBLE);

        // Set onClickListeners for the buttons
        generateCodeButton.setOnClickListener(v -> {
            generateUniqueCode(roomID, subjectCode, section);
        });

        viewStudentsButton.setOnClickListener(v -> {
            if (roomID == null || roomID.isEmpty()) {
                Toast.makeText(this, "Room ID is missing. Cannot view students.", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            String currentDate = sdf.format(new Date());

            db.collection("rooms").document(roomID)
                    .collection("students")
                    .get()
                    .addOnSuccessListener(studentSnapshots -> {
                        if (!studentSnapshots.isEmpty()) {
                            Intent intent = new Intent(Teachers_Home.this, ViewStudents.class);
                            intent.putExtra("roomId", roomID);
                            intent.putExtra("section", section);
                            intent.putExtra("subjectCode", subjectCode);
                            intent.putExtra("currentDate", currentDate); // Pass today's date
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "No students have attendance today.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error fetching student attendance: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    private void generateUniqueCode(String roomId, String subjectCode, String section) {
        // Generate today's date in YYYYMMDD format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String currentDate = sdf.format(new Date());

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Reference to today's code
        DocumentReference codeRef = db.collection("rooms").document(roomId)
                .collection("dailyCodes").document(currentDate);

        // Force Firestore to fetch fresh data (disable cache)
        codeRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                // ✅ Use existing code
                String existingCode = documentSnapshot.getString("attendanceCode");
                Log.d("GenerateCode", "✅ Existing Code Found: " + existingCode);
                openGenerateCodeActivity(existingCode, subjectCode, section);
            } else {
                // 🔥 Generate new code if it doesn't exist
                String newCode = roomId + "_" + currentDate;
                Map<String, Object> attendanceCodeData = new HashMap<>();
                attendanceCodeData.put("attendanceCode", newCode);
                attendanceCodeData.put("generatedDate", FieldValue.serverTimestamp()); // Auto-updating date field

                codeRef.set(attendanceCodeData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d("GenerateCode", "✅ New Attendance Code Generated: " + newCode);
                            openGenerateCodeActivity(newCode, subjectCode, section);
                        })
                        .addOnFailureListener(e -> Log.e("GenerateCode", "❌ Error saving new code", e));
            }
        }).addOnFailureListener(e -> Log.e("GenerateCode", "❌ Error checking existing code", e));
    }



    private void openGenerateCodeActivity(String attendanceCode, String subjectCode, String section) {
        Intent intent = new Intent(Teachers_Home.this, GenerateCode.class);
        intent.putExtra("roomCode", attendanceCode);
        intent.putExtra("subjectSection", subjectCode + " - " + section);
        startActivity(intent);
    }

}
