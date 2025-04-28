package com.example.smarttrack;

import static android.view.View.VISIBLE;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class Admins_Home extends AppCompatActivity {
    private static final String TAG = "Admins_Home";
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
    private Button registerTeacherButton;
    LinearLayout eventLayout;
    private String uid;
    private Button teacherListButton, teacherRoomsButton, studentListButton;
    private FirebaseFirestore db;
    private double latitude;
    private double longitude;
    private String address;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("Home");

        eventLayout = findViewById(R.id.eventLayout);

        registerTeacherButton = findViewById(R.id.registerTeacherButton);
        registerTeacherButton.setVisibility(VISIBLE);

        dashboardMessage = findViewById(R.id.dashboardMessage);
        dashboardMessage.setText("Welcome Admin!");
        locationTextView = findViewById(R.id.locationTextView);
        floatingWindow = findViewById(R.id.floatingWindow);
        roomsLayout = findViewById(R.id.roomsLayout);

        roomIcon = findViewById(R.id.roomIcon);
        scheduleIcon = findViewById(R.id.scheduleIcon);
        reportIcon = findViewById(R.id.reportIcon);

        db = FirebaseFirestore.getInstance();

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navUsername = navigationView.findViewById(R.id.navUsername);
        navIdNumber = navigationView.findViewById(R.id.navIdNumber);

        roomIcon.setClickable(true);
        scheduleIcon.setClickable(true);
        reportIcon.setClickable(true);

        teacherListButton = findViewById(R.id.teacherListButton);
        teacherRoomsButton = findViewById(R.id.teacherRoomsButton);
        studentListButton = findViewById(R.id.studentListButton);


        teacherListButton.setVisibility(VISIBLE);
        teacherRoomsButton.setVisibility(VISIBLE);
        studentListButton.setVisibility(VISIBLE);

        TextView manualLink = findViewById(R.id.manualLink);

        manualLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://www.figma.com/proto/vK2tOFsOzASP4N9fSOLX2m/Manual-Home?node-id=7-11&t=xZwiy5Ktj5y1SNUp-1&scaling=min-zoom&content-scaling=fixed&page-id=0%3A1";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        });


        teacherListButton.setOnClickListener(v -> {
            Intent intent = new Intent(Admins_Home.this, ListPage.class);
            intent.putExtra("title", "Teachers"); // 🔥 Pass title
            startActivity(intent);
        });

        teacherRoomsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Admins_Home.this, ListPage.class);
            intent.putExtra("title", "Teacher Rooms"); // 🔥 Pass title
            startActivity(intent);
        });

        studentListButton.setOnClickListener(v -> {
            Intent intent = new Intent(Admins_Home.this, ListPage.class);
            intent.putExtra("title", "Students"); // 🔥 Pass title
            startActivity(intent);
        });


        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));


        uid = getIntent().getStringExtra("uid");
        fetchRoomsByTeacher(uid);
        fetchUserDetailed(uid);
        fetchTeacherEventsForToday(uid);

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Admins_Home.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        registerTeacherButton.setOnClickListener(v -> {
            Intent intent = new Intent(Admins_Home.this, Register.class);
            intent.putExtra("userType", "teacher"); // Auto-set userType as teacher
            startActivity(intent);
        });

        roomIcon.setOnClickListener(v -> {
            getSupportActionBar().setTitle("Room");
            Intent intent = new Intent(Admins_Home.this, Admins_Room.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        scheduleIcon.setOnClickListener(v -> {
            getSupportActionBar().setTitle("Calendar");
            Intent intent = new Intent(Admins_Home.this, Admins_Calendar.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        reportIcon.setOnClickListener(v -> {
            getSupportActionBar().setTitle("Home");
            Intent intent = new Intent(Admins_Home.this, Admins_Report.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        fetchUserDetails(uid);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        requestLocationPermission();
    }




    private void fetchUserDetails(String uid) {
        FirebaseFirestore.getInstance().collection("administrator")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        dashboardMessage.setText("Welcome! Admin " + firstName + " " + lastName + "!");
                    } else {
                        dashboardMessage.setText("Teacher details not found.");
                    }
                })
                .addOnFailureListener(e -> dashboardMessage.setText("Error fetching teacher details."));
    }

    private void fetchUserDetailed(String uid) {
        FirebaseFirestore.getInstance().collection("administrator")
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
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
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
                String fullAddress = address.getAddressLine(0);

                this.address = fullAddress;

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
        String today = getTodayDayName();
        TextView classTitle = findViewById(R.id.classTitle);

        FirebaseFirestore.getInstance().collection("rooms")
                .whereEqualTo("adminId", uid) // 🔥 Only fetch rooms where the teacher is assigned
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, Map<String, String>> roomDetails = new HashMap<>();
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String roomId = document.getId();
                            String subjectCode = document.getString("subjectCode");
                            String section = document.getString("section");
                            String roomCode = document.getString("roomCode");
                            String endDate = document.getString("endDate");
                            List<String> schedule = (List<String>) document.get("schedule"); // Get schedule array

                            Timestamp startTimeStamp = document.getTimestamp("startTime");
                            Timestamp endTimeStamp = document.getTimestamp("endTime");

                            // Convert timestamps
                            String startTime = formatTimestamp(startTimeStamp);
                            String endTime = formatTimestamp(endTimeStamp);

                            boolean isActive = checkIfActive(endDate);

                            // ✅ Only show rooms scheduled for today
                            if (isActive && schedule != null && schedule.contains(today) && subjectCode != null && section != null) {
                                classTitle.setVisibility(View.VISIBLE);
                                classTitle.setText("Class for Today");

                                // Store details in a nested map
                                Map<String, String> detailsMap = new HashMap<>();
                                detailsMap.put("subjectCode", subjectCode);
                                detailsMap.put("section", section);
                                detailsMap.put("startTime", startTime);
                                detailsMap.put("endTime", endTime);

                                roomDetails.put(roomId, detailsMap);
                            }
                        }

                        if (!roomDetails.isEmpty()) {
                            classTitle.setVisibility(View.VISIBLE);
                            classTitle.setText("Class for Today");
                            displayRooms(roomDetails);
                        } else {
                            Log.d(TAG, "❌ No rooms scheduled for today.");
                            classTitle.setVisibility(View.VISIBLE);
                            classTitle.setText("No class for today");
                        }
                    } else {
                        Toast.makeText(Admins_Home.this, "No rooms scheduled for today.", Toast.LENGTH_SHORT).show();
                        classTitle.setVisibility(View.VISIBLE);
                        classTitle.setText("No class for today");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Admins_Home.this, "Error fetching rooms: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    classTitle.setVisibility(View.VISIBLE);
                    classTitle.setText("Failed to load class.");
                });
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp != null) {
            Date date = timestamp.toDate();
            return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date); // Convert to "7:30 AM"
        }
        return "Unknown Time"; // Default value if timestamp is null
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

    private String getTodayDayName() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    // Method to display rooms as buttons
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

            Button generateCodeButton = roomCardView.findViewById(R.id.button);
            Button viewStudentsButton = roomCardView.findViewById(R.id.button2);

            generateCodeButton.setText("Generate Code");
            viewStudentsButton.setText("View Students");

            generateCodeButton.setVisibility(VISIBLE);
            viewStudentsButton.setVisibility(VISIBLE);

            // Find views inside the layout
            TextView txtIcon = roomCardView.findViewById(R.id.txtIcon);
            TextView title = roomCardView.findViewById(R.id.title);
            TextView subtitle = roomCardView.findViewById(R.id.subtitle);
            TextView content = roomCardView.findViewById(R.id.content);
            LinearLayout dropdownContent = roomCardView.findViewById(R.id.dropdownContent);
            ImageView arrowIcon = roomCardView.findViewById(R.id.arrowIcon);

            // Set values dynamically
            txtIcon.setText(String.valueOf(subjectCode.charAt(0)).toUpperCase());
            title.setText(subjectCode);
            subtitle.setText(section);
            content.setText("🕒 " + startTime + " - " + endTime);

            // Set click listener to toggle dropdown visibility
            roomCardView.setOnClickListener(v -> {
                if (dropdownContent.getVisibility() == View.VISIBLE) {
                    dropdownContent.setVisibility(View.GONE);
                    arrowIcon.setRotation(0);
                } else {
                    dropdownContent.setVisibility(View.VISIBLE);
                    arrowIcon.setRotation(180);
                }
            });

            generateCodeButton.setOnClickListener(v -> {
                showAttendanceCodePopup(roomId);
            });

            viewStudentsButton.setOnClickListener(v -> {
                Intent intent = new Intent(Admins_Home.this, ViewAttendance.class);
                intent.putExtra("roomId", roomId);
                startActivity(intent);
            });

            // Add the roomCardView to the parent layout
            roomsLayout.addView(roomCardView);
        }
    }

    private void showAttendanceCodePopup(String roomId) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View popupView = inflater.inflate(R.layout.daily_attendance_code, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(popupView);
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();

        ImageView qrCodeImage = popupView.findViewById(R.id.qrCodeImage);
        TextView codeText = popupView.findViewById(R.id.codeText);
        RadioButton timeInButton = popupView.findViewById(R.id.timeInButton);
        RadioButton timeOutButton = popupView.findViewById(R.id.timeOutButton);

        timeInButton.setChecked(true);

        fetchAndDisplayAttendanceCodes(roomId, qrCodeImage, codeText, timeInButton, timeOutButton);
    }


    private void fetchAndDisplayAttendanceCodes(String roomId, ImageView qrCodeImage, TextView codeText,
                                                Button timeInButton, Button timeOutButton) {
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Firestore reference (single document for both Time In and Time Out)
        DocumentReference attendanceRef = db.collection("rooms").document(roomId)
                .collection("dailyCodes").document(currentDate);

        // Get attendance codes from Firestore
        attendanceRef.get().addOnSuccessListener(snapshot -> {
            String timeInCode, timeOutCode;
            Map<String, Object> data = new HashMap<>();

            data.put("latitude", latitude);
            data.put("longitude", longitude);
            data.put("address", address);

            attendanceRef.update(data)
                    .addOnSuccessListener(aVoid -> Log.d("AttendanceCode", "✅ Location updated"))
                    .addOnFailureListener(e -> Log.e("AttendanceCode", "❌ Error updating location", e));

            if (snapshot.exists()) {
                timeInCode = snapshot.getString("timeInCode");
                timeOutCode = snapshot.getString("timeOutCode");
            } else {
                // Create and save new codes
                timeInCode = generateRandomCode(10);
                timeOutCode = generateRandomCode(10);
                saveAttendanceCodes(attendanceRef, roomId, timeInCode, timeOutCode);
            }

            // Show Time In QR code by default
            updateQRCodeDisplay(timeInCode, qrCodeImage, codeText);

            timeInButton.setSelected(true);

            showLocationConfirmationModal(attendanceRef);

            // Toggle Buttons with Animation
            timeInButton.setOnClickListener(v -> animateQRCodeSwitch(qrCodeImage, codeText, timeInCode));
            timeOutButton.setOnClickListener(v -> animateQRCodeSwitch(qrCodeImage, codeText, timeOutCode));

        }).addOnFailureListener(e -> Log.e("AttendanceCode", "❌ Error fetching attendance codes", e));
    }
    private void showLocationConfirmationModal(DocumentReference attendanceRef) {
        View view = LayoutInflater.from(this).inflate(R.layout.modal, null);

        TextView modalTitle = view.findViewById(R.id.modalTitle);
        TextView modalMessage = view.findViewById(R.id.modalMessage);
        Button cancelButton = view.findViewById(R.id.cancelButton);
        Button okayButton = view.findViewById(R.id.okayButton);

        modalTitle.setText("Confirm Your Location");

        modalMessage.setText("Confirm your location:\n" +
                "📍 Address: " + address + "\n" +
                "🌍 Latitude: " + latitude + "\n" +
                "🌍 Longitude: " + longitude);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        okayButton.setOnClickListener(v -> {
            Map<String, Object> data = new HashMap<>();
            data.put("latitude", latitude);
            data.put("longitude", longitude);
            data.put("address", address);

            attendanceRef.update(data)
                    .addOnSuccessListener(aVoid -> Log.d("AttendanceCode", "✅ Location updated"))
                    .addOnFailureListener(e -> Log.e("AttendanceCode", "❌ Error updating location", e));

            dialog.dismiss();
        });

        dialog.show();
    }

    private void animateQRCodeSwitch(ImageView qrCodeImage, TextView codeText, String newCode) {
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(qrCodeImage, "alpha", 1f, 0f);
        fadeOut.setDuration(300);
        fadeOut.setInterpolator(new AccelerateDecelerateInterpolator());

        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                updateQRCodeDisplay(newCode, qrCodeImage, codeText);  // Update QR code
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(qrCodeImage, "alpha", 0f, 1f);
                fadeIn.setDuration(300);
                fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());
                fadeIn.start();
            }
        });

        fadeOut.start();
    }

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < length; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    private void saveAttendanceCodes(DocumentReference ref, String roomId, String timeInCode, String timeOutCode) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("date", FieldValue.serverTimestamp());
        data.put("timeInCode", timeInCode);
        data.put("timeOutCode", timeOutCode);
        data.put("generatedDate", FieldValue.serverTimestamp());

        ref.set(data).addOnFailureListener(e -> Log.e("GenerateCode", "❌ Error saving attendance codes", e));
    }

    private void updateQRCodeDisplay(String code, ImageView imageView, TextView textView) {
        textView.setText("CODE: " + code);
        Bitmap qrBitmap = generateQRCode(code);
        if (qrBitmap != null) {
            imageView.setImageBitmap(qrBitmap);
        }
    }

    private Bitmap generateQRCode(String content) {
        int width = 500, height = 500;

        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            Log.e("QRCode", "❌ QR Code generation failed", e);
            return null;
        }
    }

    private void fetchTeacherEventsForToday(String teacherId) {
        String todayDate = getTodayDate(); // Get today's date in yyyy-MM-dd format
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        TextView eventsTitle = findViewById(R.id.eventsTitle); // Make sure this TextView exists in your layout

        Log.d(TAG, "🔍 Fetching events for today: " + todayDate + " by teacher: " + teacherId);

        db.collection("events")
                .whereEqualTo("eventDate", todayDate)
                .whereEqualTo("teacherId", teacherId) // ✅ Filter by teacher ID
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    eventLayout.removeAllViews(); // Clear previous data

                    if (!queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "✅ Found " + queryDocumentSnapshots.size() + " events for today by teacher.");

                        // ✅ Show "Events for Today" title
                        eventsTitle.setVisibility(VISIBLE);
                        eventsTitle.setText("Events for Today");

                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String eventId = document.getId();
                            String title = document.getString("title");
                            String location = document.getString("location");
                            String startTime = document.getString("startTime");
                            String endTime = document.getString("endTime");

                            Log.d(TAG, "📌 Event: " + title + " | Location: " + location + " | Start: " + startTime + " | End: " + endTime);

                            createEventCard(eventId, title, location, startTime, endTime);
                        }
                    } else {
                        Log.d(TAG, "❌ No events found for today by the teacher.");

                        // ✅ Display "No events for today"
                        eventsTitle.setVisibility(VISIBLE);
                        eventsTitle.setText("No events for today");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🚨 Error fetching events: ", e);
                    eventsTitle.setVisibility(VISIBLE);
                    eventsTitle.setText("Failed to load events.");
                });
    }

    private String getTodayDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return dateFormat.format(Calendar.getInstance().getTime());
    }

    private void createEventCard(String eventId, String title, String location, String startTime, String endTime) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View eventCardView = inflater.inflate(R.layout.home_card, eventLayout, false);

        // Find views inside the layout
        TextView txtIcon = eventCardView.findViewById(R.id.txtIcon);
        TextView eventTitle = eventCardView.findViewById(R.id.title);
        TextView eventLocation = eventCardView.findViewById(R.id.subtitle);
        TextView eventTime = eventCardView.findViewById(R.id.content);
        LinearLayout dropdownContent = eventCardView.findViewById(R.id.dropdownContent);
        ImageView arrowIcon = eventCardView.findViewById(R.id.arrowIcon);

        Button viewStudentsButton = eventCardView.findViewById(R.id.button);

        viewStudentsButton.setText("View Students");

        viewStudentsButton.setVisibility(VISIBLE);

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

        db.collection("events").document(eventId)
                .collection("rooms")
                .get()
                .addOnSuccessListener(roomSnapshots -> {
                    if (!roomSnapshots.isEmpty()) {
                        viewStudentsButton.setOnClickListener(v -> {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            String currentDate = sdf.format(new Date());

                            Intent intent = new Intent(Admins_Home.this, ViewEventStudents.class);
                            intent.putExtra("eventId", eventId);
                            intent.putExtra("currentDate", currentDate);

                            // Collect room IDs to pass to the ViewEventStudents activity
                            ArrayList<String> roomIds = new ArrayList<>();
                            for (DocumentSnapshot roomDoc : roomSnapshots) {
                                roomIds.add(roomDoc.getId());
                            }
                            intent.putStringArrayListExtra("roomIds", roomIds);

                            startActivity(intent);
                        });
                    } else {
                        Toast.makeText(this, "No rooms found for this event.", Toast.LENGTH_SHORT).show();
                        closeFloatingWindow();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching rooms for event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    closeFloatingWindow();
                });

        runOnUiThread(() -> eventLayout.addView(eventCardView));
    }



    private void showEventFloatingWindow(String eventId) {
        View blurBackground = findViewById(R.id.blurBackground);
        floatingWindow.setVisibility(VISIBLE);
        blurBackground.setVisibility(VISIBLE);
        roomsLayout.setVisibility(View.GONE);

        Toast.makeText(this, "Event ID: " + eventId, Toast.LENGTH_SHORT).show();

        Button viewStudentsEventsButton = findViewById(R.id.viewStudentsEventsButton);
        viewStudentsEventsButton.setVisibility(VISIBLE);

        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID is missing. Cannot view events.", Toast.LENGTH_SHORT).show();
            return;
        }



        // Fetch all rooms associated with the event


        findViewById(R.id.closeFloatingWindow).setOnClickListener(v -> closeFloatingWindow());
    }

    private void closeFloatingWindow() {
        View blurBackground = findViewById(R.id.blurBackground);
        Button viewStudentsEventsButton = findViewById(R.id.viewStudentsEventsButton);

        Button generateCodeButton = findViewById(R.id.generateCodeButton);
        Button viewStudentsButton = findViewById(R.id.viewStudentsButton);

        floatingWindow.setVisibility(View.GONE);
        blurBackground.setVisibility(View.GONE);
        roomsLayout.setVisibility(VISIBLE);

        // Ensure the button is hidden when closing the floating window
        viewStudentsEventsButton.setVisibility(View.GONE);
        generateCodeButton.setVisibility(View.GONE);
        viewStudentsButton.setVisibility(View.GONE);
    }
}
