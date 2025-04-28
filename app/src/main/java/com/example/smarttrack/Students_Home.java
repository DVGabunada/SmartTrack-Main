package com.example.smarttrack;

import static android.view.View.VISIBLE;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.view.View;
import android.widget.Toast;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class Students_Home extends AppCompatActivity {
    private static final String TAG = "Students_Home";

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
    TextView studentNameTextView;
    TextView classDetailsTextView;
    TextView studentAttendanceTextView;
    TextView studentFeedbackTextView;
    TextView teacherNameTextView;
    FirebaseFirestore db;
    private String uid;
    double latitude;
    double longitude;
    private String location;
    private LinearLayout eventLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Setup toolbar and layout elements
        setupToolbarAndUI();

        // Get user ID from intent
        uid = getIntent().getStringExtra("uid");
        fetchUserDetails(uid);
        fetchUserDetailed(uid);
        checkFaceDetectionStatus(uid);

        // Fetch rooms and initialize location tracking
        fetchRoomsForToday();
        fetchEventsForToday();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        requestLocationPermission();
    }

    private void setupToolbarAndUI() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("Home");
        floatingWindow = findViewById(R.id.floatingWindow);
        dashboardMessage = findViewById(R.id.dashboardMessage);
        locationTextView = findViewById(R.id.locationTextView);
        roomsLayout = findViewById(R.id.roomsLayout);
        roomIcon = findViewById(R.id.roomIcon);
        scheduleIcon = findViewById(R.id.scheduleIcon);
        reportIcon = findViewById(R.id.reportIcon);

        studentNameTextView = findViewById(R.id.studentNameTextView);
        classDetailsTextView = findViewById(R.id.classDetailsTextView);
        studentAttendanceTextView = findViewById(R.id.studentAttendanceTextView);
        studentFeedbackTextView = findViewById(R.id.studentFeedbackTextView);
        teacherNameTextView = findViewById(R.id.teacherNameTextView);
        eventLayout = findViewById(R.id.eventLayout);



        db = FirebaseFirestore.getInstance();

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navUsername = navigationView.findViewById(R.id.navUsername);
        navIdNumber = navigationView.findViewById(R.id.navIdNumber);

        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        TextView manualLink = findViewById(R.id.manualLink);

        manualLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://www.figma.com/proto/vK2tOFsOzASP4N9fSOLX2m/Manual-Home?node-id=20-1619&t=K6Pn41feUkUp7UKy-1&scaling=min-zoom&content-scaling=fixed&page-id=20%3A1618";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        });



        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> logout());

        roomIcon.setOnClickListener(v -> startActivity(new Intent(this, Students_Room.class).putExtra("uid", uid)));
        scheduleIcon.setOnClickListener(v -> startActivity(new Intent(this, Students_Calendar.class).putExtra("uid", uid)));
        reportIcon.setOnClickListener(v -> startActivity(new Intent(this, Students_Report.class).putExtra("uid", uid)));
    }



    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(Students_Home.this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
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
                location = address.getAddressLine(0);
                locationTextView.setText("Location: " + location);
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

    private void fetchUserDetails(String uid) {
        FirebaseFirestore.getInstance().collection("students").document(uid).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        dashboardMessage.setText("Hi, Student " + firstName + " " + lastName + "!");
                    } else {
                        dashboardMessage.setText("Student details not found.");
                    }
                })
                .addOnFailureListener(e -> dashboardMessage.setText("Error fetching student details."));
    }

    private void checkFaceDetectionStatus(String userId) {
        if (userId == null) {
            Log.e("FaceDetection", "User ID is null");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseStorage storage = FirebaseStorage.getInstance();

        // Check in Firestore
        db.collection("face_detections").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Log.d("FaceDetection", "User face data exists in Firestore. No popup needed.");
                        return; // User is registered, do nothing
                    }

                    // If not found in Firestore, check Firebase Storage
                    checkFaceInStorage(userId, storage);
                })
                .addOnFailureListener(e -> Log.e("FaceDetection", "Error checking Firestore", e));
    }

    private void checkFaceInStorage(String userId, FirebaseStorage storage) {
        StorageReference faceRef = storage.getReference().child("face_detections/" + userId);

        faceRef.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    Log.d("FaceDetection", "User face data exists in Storage. No popup needed.");
                    // User is registered in Storage, do nothing
                })
                .addOnFailureListener(e -> {
                    Log.d("FaceDetection", "User face data not found. Showing registration popup.");
                    showFaceRegistrationPopup(); // Only show if not found in both Firestore and Storage
                });
    }


    private void showFaceRegistrationPopup() {
        View blurBackground2 = findViewById(R.id.blurBackground2);
        LinearLayout floatingWindow2 = findViewById(R.id.floatingWindow2);

        // Ensure visibility
        floatingWindow2.setVisibility(View.VISIBLE);
        blurBackground2.setVisibility(View.VISIBLE);

        // Clear previous content
        floatingWindow2.removeAllViews();
        floatingWindow2.setOrientation(LinearLayout.VERTICAL);
        floatingWindow2.setGravity(Gravity.CENTER_HORIZONTAL);

        // Layout params for centering the floating window
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER;
        floatingWindow2.setLayoutParams(layoutParams);

        // **Close Button (Top-Left)**
        ImageView closeFloatingWindow = new ImageView(this);
        closeFloatingWindow.setImageResource(R.drawable.back); // Ensure this is a close icon
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(80, 80);
        closeParams.gravity = Gravity.START;
        closeParams.setMargins(20, 20, 0, 0);  // Position it at the top-left
        closeFloatingWindow.setLayoutParams(closeParams);
        closeFloatingWindow.setOnClickListener(v -> {
            floatingWindow2.setVisibility(View.GONE);
            blurBackground2.setVisibility(View.GONE);
        });

        // **Title TextView (Centered)**
        TextView title = new TextView(this);
        title.setText("Register your face to proceed using the application");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(20, 10, 20, 10);

        // **ImageView (Faceregister.png, Bigger & Centered)**
        ImageView faceImage = new ImageView(this);
        faceImage.setImageResource(R.drawable.faceregister);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(500, 500); // Increase size
        imgParams.gravity = Gravity.CENTER_HORIZONTAL;  // Ensure image is centered
        imgParams.setMargins(20, 20, 20, 20);
        faceImage.setLayoutParams(imgParams);

        // **Face Registration Button**
        Button registerFaceButton = new Button(this);
        registerFaceButton.setText("Face Registration");
        registerFaceButton.setTextSize(20);
        registerFaceButton.setPadding(20, 10, 20, 10);
        registerFaceButton.setBackgroundColor(getResources().getColor(R.color.maroon));
        registerFaceButton.setTextColor(Color.WHITE);

        // **Navigate to Face Registration Activity**
        registerFaceButton.setOnClickListener(v -> {
            Intent intent = new Intent(Students_Home.this, FaceRegister.class);
            intent.putExtra("uid", uid);
            startActivity(intent);

            // Hide popup after opening face registration
            floatingWindow2.setVisibility(View.GONE);
            blurBackground2.setVisibility(View.GONE);
        });

        // **Add Views to Floating Window**
        floatingWindow2.addView(closeFloatingWindow); // Close button at top-left
        floatingWindow2.addView(title);              // Title (Centered)
        floatingWindow2.addView(faceImage);          // Image (Centered)
        floatingWindow2.addView(registerFaceButton); // Button (Centered)
    }



    private void fetchUserDetailed(String uid) {
        FirebaseFirestore.getInstance().collection("students").document(uid).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        navUsername.setText(document.getString("firstName") + " " + document.getString("lastName"));
                        navIdNumber.setText(document.getString("idNumber"));
                    }
                })
                .addOnFailureListener(e -> {
                    navUsername.setText("Error fetching details");
                    navIdNumber.setText("");
                });
    }

    private void fetchRoomsForToday() {
        String today = getTodayDayName(); // Get today's name (e.g., "Tuesday")
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        TextView classTitle = findViewById(R.id.classTitle);

        Map<String, Map<String, String>> roomDetails = new HashMap<>();

        db.collection("rooms").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Fetched rooms for today. Count: " + queryDocumentSnapshots.size());

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

                        if (isActive && document.contains("schedule")) {
                            List<String> schedule = (List<String>) document.get("schedule");
                            if (schedule.contains(today)) {
                                Map<String, String> detailsMap = new HashMap<>();
                                detailsMap.put("roomId", roomId);
                                detailsMap.put("subjectCode", subjectCode);
                                detailsMap.put("section", section);
                                detailsMap.put("startTime", startTime);
                                detailsMap.put("endTime", endTime);
                                detailsMap.put("isActive", String.valueOf(isActive));

                                // Store room details for checking student enrollment
                                roomDetails.put(roomId, detailsMap);
                            }
                        }
                    }

                    // Now check if the student is in any rooms
                    checkStudentInRoom(roomDetails);
                })
                .addOnFailureListener(e -> {
                    classTitle.setVisibility(View.VISIBLE);
                    classTitle.setText("Failed to load class.");
                    Log.e(TAG, "Error fetching rooms for today: " + e.getMessage());
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

    // Get today's day name (e.g., "Monday", "Tuesday", etc.)
    private String getTodayDayName() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    private void checkStudentInRoom(Map<String, Map<String, String>> roomDetails) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        TextView classTitle = findViewById(R.id.classTitle);

        if (roomDetails.isEmpty()) {
            // No scheduled classes for today
            classTitle.setVisibility(View.VISIBLE);
            classTitle.setText("No class for today");
            return;
        }

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        Map<String, Map<String, String>> validRooms = new HashMap<>(); // Store only rooms where the student is enrolled
        AtomicInteger studentRoomCount = new AtomicInteger(0); // Track how many rooms the student is in

        for (String roomId : roomDetails.keySet()) {
            Task<DocumentSnapshot> task = db.collection("rooms")
                    .document(roomId)
                    .collection("students")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(studentDoc -> {
                        if (studentDoc.exists()) {
                            studentRoomCount.incrementAndGet(); // Count the valid room
                            validRooms.put(roomId, roomDetails.get(roomId)); // Add to valid rooms
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error checking student in room: " + roomId, e));

            tasks.add(task);
        }

        // Wait for all Firestore tasks to complete
        Tasks.whenAllComplete(tasks)
                .addOnSuccessListener(results -> {
                    classTitle.setVisibility(View.VISIBLE);
                    if (studentRoomCount.get() > 0) {
                        classTitle.setText("Class for Today");
                        displayRooms(validRooms); // Display only rooms where the student is enrolled
                    } else {
                        classTitle.setText("No class for today");
                    }
                })
                .addOnFailureListener(e -> {
                    classTitle.setVisibility(View.VISIBLE);
                    classTitle.setText("Failed to load class.");
                    Log.e(TAG, "Error checking student enrollment: " + e.getMessage());
                });
    }

    private void displayRooms(Map<String, Map<String, String>> roomDetails) {
        roomsLayout.removeAllViews();
        roomsLayout.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);

        for (Map.Entry<String, Map<String, String>> entry : roomDetails.entrySet()) {
            String roomId = entry.getKey();
            Map<String, String> detailsMap = entry.getValue();

            String subjectCode = detailsMap.get("subjectCode");
            String section = detailsMap.get("section");
            String startTime = detailsMap.get("startTime");
            String endTime = detailsMap.get("endTime");

            View roomCardView = inflater.inflate(R.layout.home_card, roomsLayout, false);

            Button attendanceButton = roomCardView.findViewById(R.id.button);

            attendanceButton.setText("Attendance");

            attendanceButton.setVisibility(VISIBLE);

            TextView txtIcon = roomCardView.findViewById(R.id.txtIcon);
            TextView title = roomCardView.findViewById(R.id.title);
            TextView subtitle = roomCardView.findViewById(R.id.subtitle);
            TextView content = roomCardView.findViewById(R.id.content);
            LinearLayout dropdownContent = roomCardView.findViewById(R.id.dropdownContent);
            ImageView arrowIcon = roomCardView.findViewById(R.id.arrowIcon);

            txtIcon.setText(String.valueOf(subjectCode.charAt(0)).toUpperCase());
            title.setText(subjectCode);
            subtitle.setText(section);
            content.setText("🕒 " + startTime + " - " + endTime);

            roomCardView.setOnClickListener(v -> {
                if (dropdownContent.getVisibility() == View.VISIBLE) {
                    dropdownContent.setVisibility(View.GONE);
                    arrowIcon.setRotation(0);
                } else {
                    dropdownContent.setVisibility(View.VISIBLE);
                    arrowIcon.setRotation(180);
                }
            });

            attendanceButton.setOnClickListener(v -> {
                Intent intent = new Intent(Students_Home.this, AttendanceRoom.class);
                intent.putExtra("uid", uid);
                intent.putExtra("roomId", roomId);
                startActivity(intent);
            });

            roomsLayout.addView(roomCardView);
        }
    }


    private void showFloatingWindow(String roomId) {
        Log.d(TAG, "✅ Room ID: " + roomId);
        View blurBackground = findViewById(R.id.blurBackground);
        floatingWindow.setVisibility(View.VISIBLE);
        blurBackground.setVisibility(View.VISIBLE);
        roomsLayout.setVisibility(View.GONE);

        Button timeInButton = findViewById(R.id.timeInButton);
        Button timeOutButton = findViewById(R.id.timeOutButton);

        studentNameTextView.setVisibility(View.VISIBLE);
        classDetailsTextView.setVisibility(View.VISIBLE);
        studentAttendanceTextView.setVisibility(View.VISIBLE);
        studentFeedbackTextView.setVisibility(View.VISIBLE);
        teacherNameTextView.setVisibility(View.VISIBLE);

        timeInButton.setVisibility(View.GONE);
        timeOutButton.setVisibility(View.GONE);

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Fetch student name
        db.collection("students").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String fullName = formatFullName(
                                documentSnapshot.getString("firstName"),
                                documentSnapshot.getString("middleName"),
                                documentSnapshot.getString("lastName")
                        );
                        studentNameTextView.setText("Student: " + fullName);
                    } else {
                        studentNameTextView.setText("Student: Unknown");
                    }
                });

        db.collection("rooms").document(roomId)
                .get()
                .addOnSuccessListener(roomDoc -> {
                    if (roomDoc.exists()) {
                        String subjectName = roomDoc.getString("subjectName");
                        String subjectCode = roomDoc.getString("subjectCode");
                        String section = roomDoc.getString("section");
                        String teacherId = roomDoc.getString("teacherId");

                        Timestamp startTimeStamp = roomDoc.getTimestamp("startTime");
                        Timestamp endTimeStamp = roomDoc.getTimestamp("endTime");

                        String startTime = formatTimestamp(startTimeStamp);
                        String endTime = formatTimestamp(endTimeStamp);

                        classDetailsTextView.setText(
                                "Subject: " + subjectName + " (" + subjectCode + ")\n" +
                                        "Section: " + section + "\n" +
                                        "Class Time: " + startTime + " - " + endTime + "\n"
                        );

                        // Fetch teacher or admin name
                        if (teacherId != null) {
                            db.collection("teachers").document(teacherId)
                                    .get()
                                    .addOnSuccessListener(teacherDoc -> {
                                        if (teacherDoc.exists()) {
                                            String teacherName = formatFullName(
                                                    teacherDoc.getString("firstName"),
                                                    teacherDoc.getString("middleName"),
                                                    teacherDoc.getString("lastName")
                                            );
                                            teacherNameTextView.setText("Teacher: " + teacherName);
                                        } else {
                                            // Fallback to admin if teacher is not found
                                            db.collection("administrator").document(teacherId)
                                                    .get()
                                                    .addOnSuccessListener(adminDoc -> {
                                                        if (adminDoc.exists()) {
                                                            String adminName = formatFullName(
                                                                    adminDoc.getString("firstName"),
                                                                    adminDoc.getString("middleName"),
                                                                    adminDoc.getString("lastName")
                                                            );
                                                            teacherNameTextView.setText("Admin: " + adminName);
                                                        } else {
                                                            teacherNameTextView.setText("Admin: Unknown");
                                                        }
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        teacherNameTextView.setText("Error fetching admin details");
                                                    });
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        teacherNameTextView.setText("Error fetching teacher details");
                                    });
                        } else {
                            teacherNameTextView.setText("Teacher/Admin: Unknown");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    classDetailsTextView.setText("Error fetching class details");
                    teacherNameTextView.setText("Error fetching teacher/admin details");
                });


        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date startOfDay = calendar.getTime();

        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        Date endOfDay = calendar.getTime();

        db.collection("rooms").document(roomId)
                .collection("students").document(uid)
                .collection("attendance")
                .whereGreaterThanOrEqualTo("date", startOfDay)
                .whereLessThanOrEqualTo("date", endOfDay)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot attendanceDoc = queryDocumentSnapshots.getDocuments().get(0);
                        String status = attendanceDoc.getString("status");
                        Timestamp timeInTimestamp = attendanceDoc.getTimestamp("timeIn");
                        Timestamp timeOutTimestamp = attendanceDoc.getTimestamp("timeOut");
                        String feedback = attendanceDoc.getString("feedback");

                        String timeIn = timeInTimestamp != null ? formatTimestamp(timeInTimestamp) : "N/A";
                        String timeOut = timeOutTimestamp != null ? formatTimestamp(timeOutTimestamp) : "N/A";

                        studentAttendanceTextView.setText(
                                "Time In: " + timeIn + "\n" +
                                        "Time Out: " + timeOut + "\n" +
                                        "Status: " + (status != null ? status : "N/A")
                        );

                        if (feedback != null && !feedback.isEmpty()) {
                            studentFeedbackTextView.setVisibility(View.VISIBLE);
                            studentFeedbackTextView.setText("Feedback: " + feedback);
                        } else {
                            studentFeedbackTextView.setVisibility(View.GONE);
                        }
                    } else {
                        studentAttendanceTextView.setText("No attendance record for today.");
                        studentFeedbackTextView.setVisibility(View.GONE);
                    }
                });

        checkAttendanceStatus(roomId, timeInButton, timeOutButton);

        timeInButton.setOnClickListener(v -> {
            Intent intent = new Intent(Students_Home.this, ScanQRTimeIn.class);
            intent.putExtra("uid", uid);
            intent.putExtra("roomId", roomId);
            intent.putExtra("latitude", latitude);
            intent.putExtra("longitude", longitude);
            intent.putExtra("location", location);
            startActivity(intent);
            closeFloatingWindow(blurBackground);
        });

        timeOutButton.setOnClickListener(v -> {
            showFeedbackDialog(roomId);
            closeFloatingWindow(blurBackground);
        });

        findViewById(R.id.closeFloatingWindow).setOnClickListener(v -> closeFloatingWindow(blurBackground));
    }

    private String formatFullName(String firstName, String middleName, String lastName) {
        return (firstName != null ? firstName + " " : "") +
                (middleName != null ? middleName + " " : "") +
                (lastName != null ? lastName : "").trim();
    }

    private void closeFloatingWindow(View blurBackground) {
        floatingWindow.setVisibility(View.GONE);
        blurBackground.setVisibility(View.GONE);
        roomsLayout.setVisibility(View.VISIBLE);

        studentNameTextView.setVisibility(View.GONE);
        classDetailsTextView.setVisibility(View.GONE);
        studentAttendanceTextView.setVisibility(View.GONE);
        studentFeedbackTextView.setVisibility(View.GONE);
        teacherNameTextView.setVisibility(View.GONE);
    }

    private void checkAttendanceStatus(String roomId, Button timeInButton, Button timeOutButton) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get today's date in the format "yyyyMMdd" to match Firestore document naming
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

        // Reference to today's attendance document under the correct room path
        DocumentReference attendanceRef = db.collection("rooms").document(roomId)
                .collection("students").document(uid)
                .collection("attendance").document(currentDate);

        attendanceRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                boolean hasTimeIn = documentSnapshot.contains("timeIn");
                boolean hasTimeOut = documentSnapshot.contains("timeOut");

                if (!hasTimeIn) {
                    timeInButton.setVisibility(View.VISIBLE);  // ✅ Show Time In
                    timeOutButton.setVisibility(View.GONE);    // ❌ Hide Time Out
                } else if (!hasTimeOut) {
                    timeInButton.setVisibility(View.GONE);    // ❌ Hide Time In
                    timeOutButton.setVisibility(View.VISIBLE); // ✅ Show Time Out
                } else {
                    timeInButton.setVisibility(View.GONE);  // ❌ Hide Time In
                    timeOutButton.setVisibility(View.GONE); // ❌ Hide Time Out
                }
            } else {
                timeInButton.setVisibility(View.VISIBLE);  // ✅ No attendance yet today, show Time In
                timeOutButton.setVisibility(View.GONE);    // ❌ Hide Time Out
            }
        }).addOnFailureListener(e -> Log.e("AttendanceStatus", "❌ Error checking attendance: ", e));
    }

    private void showFeedbackDialog(String roomId) {
        View dialogView = getLayoutInflater().inflate(R.layout.activity_feedback_dialog, null);
        EditText feedbackEditText = dialogView.findViewById(R.id.feedbackEditText);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button submitButton = dialogView.findViewById(R.id.submitButton);

        // Create and show the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();

        // Handle cancel button click
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Handle submit button click
        submitButton.setOnClickListener(v -> {
            String feedback = feedbackEditText.getText().toString().trim();
            if (!feedback.isEmpty()) {
                dialog.dismiss();
                proceedWithTimeOut(roomId, feedback); // Proceed with the time-out process
            } else {
                Toast.makeText(this, "Feedback cannot be empty!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void proceedWithTimeOut(String roomId, String feedback) {
        Intent intent = new Intent(Students_Home.this, ScanQRTimeOut.class);
        intent.putExtra("roomId", roomId);
        intent.putExtra("feedback", feedback);
        startActivity(intent);

    }

    private void fetchEventsForToday() {
        String todayDate = getTodayDate(); // Get today’s date in yyyy-MM-dd format
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        LinearLayout eventLayout = findViewById(R.id.eventLayout);
        TextView eventsTitle = findViewById(R.id.eventsTitle); // Make sure this TextView exists in your layout

        Log.d(TAG, "🔍 Fetching events for today: " + todayDate);

        db.collection("events")
                .whereEqualTo("eventDate", todayDate)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    eventLayout.removeAllViews(); // Clear previous data

                    if (!queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "✅ Found " + queryDocumentSnapshots.size() + " events for today.");

                        // ✅ Show "Events for Today" title
                        eventsTitle.setVisibility(View.VISIBLE);
                        eventsTitle.setText("Events for Today");

                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String eventId = document.getId();
                            String title = document.getString("title");
                            String location = document.getString("location");
                            String startTime = document.getString("startTime");
                            String endTime = document.getString("endTime");

                            Log.d(TAG, "📌 Event: " + title + " | Location: " + location + " | Start: " + startTime + " | End: " + endTime);

                            checkAndCreateEventCard(eventId, title, location, startTime, endTime);
                        }
                    } else {
                        Log.d(TAG, "❌ No events found for today.");

                        // ✅ Hide "Events for Today" title and display "No events for today"
                        eventsTitle.setVisibility(View.VISIBLE);
                        eventsTitle.setText("No events for today");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🚨 Error fetching events: ", e);
                    eventsTitle.setVisibility(View.VISIBLE);
                    eventsTitle.setText("Failed to load events.");
                });
    }

    private void checkAndCreateEventCard(String eventId, String title, String location, String startTime, String endTime) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Log.d("EventCheck", "Checking event: " + eventId + " for student UID: " + uid);

        // Step 1: Get the rooms associated with the event
        db.collection("events").document(eventId).collection("rooms")
                .get()
                .addOnSuccessListener(eventRoomsSnapshot -> {
                    List<String> eventRoomIds = new ArrayList<>();

                    for (DocumentSnapshot roomDoc : eventRoomsSnapshot.getDocuments()) {
                        eventRoomIds.add(roomDoc.getId());
                    }

                    if (eventRoomIds.isEmpty()) {
                        Log.d("EventCheck", "No rooms found for event: " + eventId);
                        return;
                    }

                    Log.d("EventCheck", "Rooms found for event: " + eventRoomIds);

                    // Step 2: Check if the student is in any of these rooms
                    checkStudentInRooms(db, eventId, title, location, startTime, endTime, eventRoomIds);
                })
                .addOnFailureListener(e -> Log.e("EventCheck", "Error fetching rooms for event", e));
    }

    private void checkStudentInRooms(FirebaseFirestore db, String eventId, String title, String location, String startTime, String endTime, List<String> eventRoomIds) {
        AtomicBoolean isStudentInRoom = new AtomicBoolean(false); // ✅ Use AtomicBoolean to track across async calls

        for (String roomId : eventRoomIds) {
            db.collection("rooms").document(roomId).collection("students")
                    .get()
                    .addOnSuccessListener(studentSnapshot -> {
                        for (DocumentSnapshot studentDoc : studentSnapshot.getDocuments()) {
                            String studentId = studentDoc.getId();
                            Log.d("EventCheck", "Room: " + roomId + " | Student found: " + studentId);

                            if (studentId.equals(uid)) {
                                isStudentInRoom.set(true);
                                Log.d("EventCheck", "✅ Student " + uid + " found in room: " + roomId);

                                // Create the event card and return immediately to prevent duplicate calls
                                createEventCard(eventId, title, location, startTime, endTime);
                                return;
                            }
                        }

                        if (!isStudentInRoom.get()) {
                            Log.d("EventCheck", "❌ Student " + uid + " NOT in room: " + roomId);
                        }
                    })
                    .addOnFailureListener(e -> Log.e("EventCheck", "Error fetching students for room: " + roomId, e));
        }
    }



    private void createEventCard(String eventId, String title, String location, String startTime, String endTime) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View eventCardView = inflater.inflate(R.layout.home_card, eventLayout, false);

        TextView txtIcon = eventCardView.findViewById(R.id.txtIcon);
        TextView eventTitle = eventCardView.findViewById(R.id.title);
        TextView eventLocation = eventCardView.findViewById(R.id.subtitle);
        TextView eventTime = eventCardView.findViewById(R.id.content);
        LinearLayout dropdownContent = eventCardView.findViewById(R.id.dropdownContent);
        ImageView arrowIcon = eventCardView.findViewById(R.id.arrowIcon);

        Button attendanceButton = eventCardView.findViewById(R.id.button);
        attendanceButton.setText("Attendance");
        attendanceButton.setVisibility(View.VISIBLE);

        txtIcon.setText(String.valueOf(title.charAt(0)).toUpperCase());
        eventTitle.setText(title);
        eventLocation.setText("📍 " + location);
        eventTime.setText("🕒 " + startTime + " - " + endTime);

        eventCardView.setOnClickListener(v -> {
            if (dropdownContent.getVisibility() == View.VISIBLE) {
                dropdownContent.setVisibility(View.GONE);
                arrowIcon.setRotation(0);
            } else {
                dropdownContent.setVisibility(View.VISIBLE);
                arrowIcon.setRotation(180);
            }
        });

        attendanceButton.setOnClickListener(v -> {
            Intent intent = new Intent(Students_Home.this, AttendanceEvent.class);
            intent.putExtra("uid", uid);
            intent.putExtra("eventId", eventId);
            intent.putExtra("latitude", latitude);
            intent.putExtra("longitude", longitude);
            intent.putExtra("location", location);
            startActivity(intent);
        });

        runOnUiThread(() -> eventLayout.addView(eventCardView));
    }


    private void showEventFloatingWindow(String eventId) {
        View blurBackground = findViewById(R.id.blurBackground);
        floatingWindow.setVisibility(View.VISIBLE);
        blurBackground.setVisibility(View.VISIBLE);
        roomsLayout.setVisibility(View.GONE);

        Button timeInButton = findViewById(R.id.timeInButton);
        Button timeOutButton = findViewById(R.id.timeOutButton);

        studentNameTextView.setVisibility(View.VISIBLE);
        classDetailsTextView.setVisibility(View.VISIBLE);
        studentAttendanceTextView.setVisibility(View.VISIBLE);
        studentFeedbackTextView.setVisibility(View.VISIBLE);
        teacherNameTextView.setVisibility(View.VISIBLE);

        timeInButton.setVisibility(View.GONE);
        timeOutButton.setVisibility(View.GONE);

        checkEventAttendanceStatus(eventId, timeInButton, timeOutButton);

        // Fetch student details
        db.collection("students").document(uid)
                .get()
                .addOnSuccessListener(studentDoc -> {
                    if (studentDoc.exists()) {
                        String studentName = formatFullName(
                                studentDoc.getString("firstName"),
                                studentDoc.getString("middleName"),
                                studentDoc.getString("lastName")
                        );
                        studentNameTextView.setText("Student: " + studentName);
                    } else {
                        studentNameTextView.setText("Student: Unknown");
                    }
                });

        // Fetch event and organizer (teacher/admin) details
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(eventDoc -> {
                    if (eventDoc.exists()) {
                        String eventTitle = eventDoc.getString("title");
                        String location = eventDoc.getString("location");
                        String teacherId = eventDoc.getString("teacherId");

                        String startTime = eventDoc.getString("startTime");
                        String endTime = eventDoc.getString("endTime");

                        classDetailsTextView.setText(
                                "Event: " + eventTitle + "\n" +
                                        "Location: " + location + "\n" +
                                        "Time: " + startTime + " - " + endTime
                        );

                        // Fetch teacher/admin name
                        if (teacherId != null) {
                            db.collection("teachers").document(teacherId)
                                    .get()
                                    .addOnSuccessListener(teacherDoc -> {
                                        if (teacherDoc.exists()) {
                                            String teacherName = formatFullName(
                                                    teacherDoc.getString("firstName"),
                                                    teacherDoc.getString("middleName"),
                                                    teacherDoc.getString("lastName")
                                            );
                                            teacherNameTextView.setText("Organizer: " + teacherName);
                                        } else {
                                            fetchAdminDetails(teacherId, teacherNameTextView);
                                        }
                                    })
                                    .addOnFailureListener(e -> fetchAdminDetails(teacherId, teacherNameTextView));
                        } else {
                            teacherNameTextView.setText("Organizer: Unknown");
                        }
                    }
                });

        timeInButton.setOnClickListener(v -> {
            Intent intent = new Intent(Students_Home.this, FaceRecognition.class);
            intent.putExtra("eventId", eventId);
            intent.putExtra("uid", uid);
            startActivity(intent);
            closeFloatingWindow(blurBackground);
        });

        timeOutButton.setOnClickListener(v -> {
            showEventFeedbackDialog(eventId);
            closeFloatingWindow(blurBackground);
        });

        findViewById(R.id.closeFloatingWindow).setOnClickListener(v -> {
            closeFloatingWindow(blurBackground);
        });
    }

    private void fetchAdminDetails(String adminId, TextView teacherNameTextView) {
        db.collection("administrator").document(adminId)
                .get()
                .addOnSuccessListener(adminDoc -> {
                    if (adminDoc.exists()) {
                        String adminName = formatFullName(
                                adminDoc.getString("firstName"),
                                adminDoc.getString("middleName"),
                                adminDoc.getString("lastName")
                        );
                        teacherNameTextView.setText("Organizer: " + adminName);
                    } else {
                        teacherNameTextView.setText("Organizer: Unknown");
                    }
                })
                .addOnFailureListener(e -> {
                    teacherNameTextView.setText("Error fetching organizer details");
                });
    }


    private void checkEventAttendanceStatus(String eventId, Button timeInButton, Button timeOutButton) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date startOfDay = calendar.getTime();

        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        Date endOfDay = calendar.getTime();

        db.collection("events").document(eventId)
                .collection("rooms")
                .get()
                .addOnSuccessListener(eventRoomsSnapshot -> {
                    if (eventRoomsSnapshot.isEmpty()) {
                        debugMessage("❌ No rooms found for this event.");
                        return;
                    }

                    AtomicBoolean attendanceProcessed = new AtomicBoolean(false); // ✅ Flag to prevent duplicate UI updates

                    for (DocumentSnapshot eventRoomDoc : eventRoomsSnapshot.getDocuments()) {
                        String roomId = eventRoomDoc.getId();

                        db.collection("events").document(eventId)
                                .collection("rooms").document(roomId)
                                .collection("students").document(uid)
                                .collection("attendance")
                                .whereGreaterThanOrEqualTo("date", startOfDay)
                                .whereLessThanOrEqualTo("date", endOfDay)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(queryDocumentSnapshots -> {
                                    if (!queryDocumentSnapshots.isEmpty() && !attendanceProcessed.get()) {
                                        attendanceProcessed.set(true); // ✅ Mark attendance as processed

                                        DocumentSnapshot documentSnapshot = queryDocumentSnapshots.getDocuments().get(0);
                                        boolean hasTimeIn = documentSnapshot.contains("timeIn");
                                        boolean hasTimeOut = documentSnapshot.contains("timeOut");
                                        String timeIn = formatTimestamp(documentSnapshot.getTimestamp("timeIn"));
                                        String timeOut = formatTimestamp(documentSnapshot.getTimestamp("timeOut"));
                                        String feedback = documentSnapshot.getString("feedback");

                                        studentAttendanceTextView.setText(
                                                "Time In: " + timeIn + "\nTime Out: " + timeOut
                                        );

                                        if (feedback != null && !feedback.isEmpty()) {
                                            studentFeedbackTextView.setText("Feedback: " + feedback);
                                        } else {
                                            studentFeedbackTextView.setText("Feedback: None provided");
                                        }

                                        if (!hasTimeIn) {
                                            debugMessage("✅ Student has not timed in yet. Showing Time In button.");
                                            timeInButton.setVisibility(View.VISIBLE);
                                            timeOutButton.setVisibility(View.GONE);
                                        } else if (!hasTimeOut) {
                                            debugMessage("✅ Student has timed in but not out. Showing Time Out button.");
                                            timeInButton.setVisibility(View.GONE);
                                            timeOutButton.setVisibility(View.VISIBLE);
                                        } else {
                                            debugMessage("✅ Student has already timed in and out. Hiding buttons.");
                                            timeInButton.setVisibility(View.GONE);
                                            timeOutButton.setVisibility(View.GONE);
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> debugMessage("❌ Error checking attendance: " + e.getMessage()));
                    }

                    // ✅ If no attendance was found after checking all rooms, update UI **only once**
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!attendanceProcessed.get()) {
                            debugMessage("✅ No attendance record found. Showing Time In button.");
                            timeInButton.setVisibility(View.VISIBLE);
                            timeOutButton.setVisibility(View.GONE);
                            studentAttendanceTextView.setText("No attendance recorded for this event.");
                            studentFeedbackTextView.setText("Feedback: None provided");
                        }
                    }, 500); // Small delay to wait for Firebase callbacks
                })
                .addOnFailureListener(e -> debugMessage("❌ Error fetching event rooms: " + e.getMessage()));
    }

    private String getTodayDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return dateFormat.format(Calendar.getInstance().getTime());
    }

    private void showEventFeedbackDialog(String eventId) {
        // Inflate the custom layout for the dialog
        View dialogView = getLayoutInflater().inflate(R.layout.activity_feedback_dialog, null);
        EditText feedbackEditText = dialogView.findViewById(R.id.feedbackEditText);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button submitButton = dialogView.findViewById(R.id.submitButton);

        // Create and show the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();

        // Handle cancel button click
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Handle submit button click
        submitButton.setOnClickListener(v -> {
            String feedback = feedbackEditText.getText().toString().trim();
            if (!feedback.isEmpty()) {
                dialog.dismiss();
                proceedWithEventTimeOut(eventId, feedback); // Proceed with the time-out process for events
            } else {
                Toast.makeText(this, "Feedback cannot be empty!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void proceedWithEventTimeOut(String eventId, String feedback) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            debugMessage("❌ Location permission not granted!");
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        double latitude = lastKnownLocation != null ? lastKnownLocation.getLatitude() : 0.0;
        double longitude = lastKnownLocation != null ? lastKnownLocation.getLongitude() : 0.0;


        getAddressAndPrompt(eventId, latitude, longitude, feedback);
    }

    private void getAddressAndPrompt(String eventId, double latitude, double longitude, String feedback) {
        String address = "Unknown Location";

        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                address = addresses.get(0).getAddressLine(0); // Get full address
            }
        } catch (Exception e) {
            debugMessage("⚠️ Error getting address: " + e.getMessage());
        }

        promptForAddressConfirmation(eventId, address, feedback);
    }

    private void promptForAddressConfirmation(String eventId, String address, String feedback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Address");
        builder.setMessage("Detected Address:\n" + address);

        builder.setPositiveButton("OK", (dialog, which) -> {
            Timestamp timeOut = Timestamp.now();
            saveTimeOutData(eventId, timeOut, address, feedback);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            debugMessage("❌ Attendance recording cancelled.");
            dialog.dismiss();
        });

        builder.show();
    }

    private void saveTimeOutData(String eventId, Timestamp timeOut, String address, String feedback) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Find the correct room for this student in the event
        db.collection("events").document(eventId)
                .collection("rooms")
                .get()
                .addOnSuccessListener(eventRoomsSnapshot -> {
                    if (eventRoomsSnapshot.isEmpty()) {
                        debugMessage("❌ No rooms found for this event. Time Out failed.");
                        return;
                    }

                    for (DocumentSnapshot eventRoomDoc : eventRoomsSnapshot.getDocuments()) {
                        String roomId = eventRoomDoc.getId(); // ✅ Get the Room ID

                        // Check if the student is inside this event's room
                        db.collection("events").document(eventId)
                                .collection("rooms").document(roomId)
                                .collection("students").document(uid)
                                .collection("attendance")
                                .orderBy("timeIn", Query.Direction.DESCENDING) // ✅ Find latest attendance
                                .limit(1)
                                .get()
                                .addOnSuccessListener(attendanceDocs -> {
                                    if (!attendanceDocs.isEmpty()) {
                                        DocumentSnapshot attendanceDoc = attendanceDocs.getDocuments().get(0);
                                        String attendanceId = attendanceDoc.getId(); // ✅ Get attendance document ID

                                        // ✅ Update existing attendance record with timeOut data
                                        Map<String, Object> timeOutData = new HashMap<>();
                                        timeOutData.put("timeOut", timeOut);
                                        timeOutData.put("locationTimeOut", address);
                                        timeOutData.put("feedback", feedback);

                                        db.collection("events").document(eventId)
                                                .collection("rooms").document(roomId)
                                                .collection("students").document(uid)
                                                .collection("attendance").document(attendanceId) // ✅ Update same attendance record
                                                .update(timeOutData)
                                                .addOnSuccessListener(aVoid -> {
                                                    debugMessage("✅ Time Out updated successfully in room: " + roomId + " at " + address);
                                                })
                                                .addOnFailureListener(e -> debugMessage("❌ Error updating Time Out: " + e.getMessage()));
                                    } else {
                                        debugMessage("❌ No existing attendance record found. Cannot time out.");
                                    }
                                })
                                .addOnFailureListener(e -> debugMessage("❌ Error fetching attendance record: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> debugMessage("❌ Error fetching event rooms: " + e.getMessage()));
    }

    private void debugMessage(String message) {
        Log.d("DEBUG_LOG", message);
        runOnUiThread(() -> Toast.makeText(Students_Home.this, message, Toast.LENGTH_SHORT).show());
    }
}