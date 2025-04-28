package com.example.smarttrack;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceRoom extends AppCompatActivity {
    private FirebaseFirestore db;
    private String uid, roomId, location;
    private double longitude, latitude;
    private TextView toolbarTitle, subjectCodeField, sectionField, startTimeField, endTimeField;
    private TextView timeInField, locationTimeInField, statusTimeInField, locationField, teacherField;
    private TextView timeOutField, locationTimeOutField, statusTimeOutField, feedbackField;
    private Button timeInButton, timeOutButton;
    private LocationManager locationManager;
    private boolean modalShown  = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        // Initialize Views
        toolbarTitle = findViewById(R.id.toolbarTitle);
        subjectCodeField = findViewById(R.id.subjectCodeField);
        sectionField = findViewById(R.id.sectionField);
        startTimeField = findViewById(R.id.startTimeField);
        endTimeField = findViewById(R.id.endTimeField);
        timeInField = findViewById(R.id.timeInField);
        locationTimeInField = findViewById(R.id.locationTimeInField);
        teacherField = findViewById(R.id.teacherField);
        statusTimeInField = findViewById(R.id.statusTimeInField);
        timeOutField = findViewById(R.id.timeOutField);
        locationTimeOutField = findViewById(R.id.locationTimeOutField);
        statusTimeOutField = findViewById(R.id.statusTimeOutField);
        timeInButton = findViewById(R.id.timeInButton);
        timeOutButton = findViewById(R.id.timeOutButton);
        feedbackField = findViewById(R.id.feedbackField);
        locationField = findViewById(R.id.locationField);

        uid = getIntent().getStringExtra("uid");
        roomId = getIntent().getStringExtra("roomId");

        db = FirebaseFirestore.getInstance();

        findViewById(R.id.backButton).setOnClickListener(v -> onBackPressed());

        fetchRoomDetails();

        checkAttendanceStatus(roomId);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        timeInButton.setOnClickListener(v -> handleTimeAction(true));
        timeOutButton.setOnClickListener(v -> handleTimeAction(false));
    }



    private void fetchRoomDetails() {
        if (roomId == null || uid == null) {
            Toast.makeText(this, "Invalid room or user.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("rooms").document(roomId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        toolbarTitle.setText(documentSnapshot.getString("subjectName"));
                        subjectCodeField.setText(documentSnapshot.getString("subjectCode"));
                        sectionField.setText(documentSnapshot.getString("section"));

                        String teacherId = documentSnapshot.getString("teacherId");

                        // Safely handle startTime and endTime
                        Object startTimeObj = documentSnapshot.get("startTime");
                        Object endTimeObj = documentSnapshot.get("endTime");

                        startTimeField.setText("Start Time: " + formatTime(startTimeObj));
                        endTimeField.setText("End Time: " + formatTime(endTimeObj));

                        fetchRoomLocation(roomId);

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
                                            teacherField.setText("Organizer: " + teacherName);
                                        } else {
                                            fetchAdminDetails(teacherId);
                                        }
                                    })
                                    .addOnFailureListener(e -> fetchAdminDetails(teacherId));
                        } else {
                            teacherField.setText("Organizer: Unknown");
                        }

                        fetchActiveSession();
                    } else {
                        toolbarTitle.setText("Class Not Found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("General", "Error fetching room details", e);
                    toolbarTitle.setText("Error Loading Class");
                });
    }

    private void fetchRoomLocation(String roomId) {
        String todayDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()); // 20250330 format

        db.collection("rooms").document(roomId)
                .collection("dailyCodes").document(todayDate)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String address = documentSnapshot.getString("address");
                        Double latitude = documentSnapshot.getDouble("latitude");
                        Double longitude = documentSnapshot.getDouble("longitude");

                        if (address != null) {
                            locationField.setText(address);
                        } else if (latitude != null && longitude != null) {
                            locationField.setText("Lat: " + latitude + ", Lng: " + longitude);
                        } else {
                            locationField.setText("Location: Not Available");
                        }
                    } else {
                        locationField.setText("Location: Not Available");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("General", "Error fetching location", e);
                    locationField.setText("Error Loading Location");
                });
    }

    private String formatFullName(String firstName, String middleName, String lastName) {
        return (firstName != null ? firstName + " " : "") +
                (middleName != null ? middleName + " " : "") +
                (lastName != null ? lastName : "").trim();
    }

    private void fetchAdminDetails(String adminId) {
        db.collection("administrator").document(adminId)
                .get()
                .addOnSuccessListener(adminDoc -> {
                    if (adminDoc.exists()) {
                        String adminName = formatFullName(
                                adminDoc.getString("firstName"),
                                adminDoc.getString("middleName"),
                                adminDoc.getString("lastName")
                        );
                        teacherField.setText("Organizer: " + adminName);
                    } else {
                        teacherField.setText("Organizer: Unknown");
                    }
                })
                .addOnFailureListener(e -> {
                    teacherField.setText("Error fetching organizer details");
                });
    }

    private String formatTime(Object timeObj) {
        if (timeObj instanceof String) {
            return (String) timeObj;
        } else if (timeObj instanceof Long) {
            return new java.text.SimpleDateFormat("hh:mm a").format(new java.util.Date((Long) timeObj));
        } else if (timeObj instanceof com.google.firebase.Timestamp) {
            return new java.text.SimpleDateFormat("hh:mm a").format(((com.google.firebase.Timestamp) timeObj).toDate());
        }
        return "Unknown";
    }

    private void fetchActiveSession() {
        if (roomId == null || uid == null) {
            Log.e("General", "Invalid room or user ID");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String todayDate = sdf.format(new Date());

        db.collection("rooms").document(roomId)
                .collection("students").document(uid)
                .collection("attendance").document(todayDate)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Time In Details
                        Object timeIn = documentSnapshot.get("timeIn");
                        String locationTimeIn = documentSnapshot.getString("locationTimeIn");
                        String statusTimeIn = documentSnapshot.getString("statusTimeIn");

                        // Update UI for Time In
                        timeInField.setText("Time In: " + formatTime(timeIn));
                        locationTimeInField.setText("Location: " + (locationTimeIn != null ? locationTimeIn : "N/A"));
                        statusTimeInField.setText("Status: " + (statusTimeIn != null ? statusTimeIn : "Absent"));

                        // Check if Time Out exists before updating
                        if (documentSnapshot.contains("timeOut")) {
                            Object timeOut = documentSnapshot.get("timeOut");
                            String locationTimeOut = documentSnapshot.getString("locationTimeOut");
                            String statusTimeOut = documentSnapshot.getString("statusTimeOut");
                            String feedback = documentSnapshot.getString("feedback");

                            // Update UI for Time Out
                            timeOutField.setText("Time Out: " + formatTime(timeOut));
                            locationTimeOutField.setText("Location: " + (locationTimeOut != null ? locationTimeOut : "N/A"));
                            statusTimeOutField.setText("Status: " + (statusTimeOut != null ? statusTimeOut : "Not Timed Out"));
                            feedbackField.setText("Feedback: " +feedback != null ? feedback : "No feedback provided");
                        } else {
                            // Hide or reset Time Out fields if no record exists
                            timeOutField.setText("Time Out: N/A");
                            locationTimeOutField.setText("Location: N/A");
                            statusTimeOutField.setText("Status: Not Timed Out");
                            feedbackField.setText("No feedback provided");
                        }
                    } else {
                        // Student has no record (Absent)
                        timeInField.setText("Time In: N/A");
                        locationTimeInField.setText("Location: N/A");
                        statusTimeInField.setText("Status: No Active Session");

                        timeOutField.setText("Time Out: N/A");
                        locationTimeOutField.setText("Location: N/A");
                        statusTimeOutField.setText("Status: Not Timed Out");
                        feedbackField.setText("No feedback provided");
                    }
                })
                .addOnFailureListener(e -> Log.e("General", "Error fetching attendance", e));
    }

    private void handleTimeAction(boolean isTimeIn) {
        getCurrentLocation(isTimeIn);
    }

    private void getCurrentLocation(boolean isTimeIn) {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show();
            showConfirmationModal("Location not available", isTimeIn);
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                locationManager.removeUpdates(this);
                getAddressFromLocation(isTimeIn);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {
                showConfirmationModal("Location not available", isTimeIn);
            }
        });
    }

    private void getAddressFromLocation(boolean isTimeIn) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        new Thread(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                location = addresses.isEmpty() ? "Unknown Location" : addresses.get(0).getAddressLine(0);
            } catch (IOException e) {
                e.printStackTrace();
                location = "Unknown Location";
            }

            runOnUiThread(() -> showConfirmationModal(location, isTimeIn));
        }).start();
    }

    private void showConfirmationModal(String locationMessage, boolean isTimeIn) {
        if (modalShown) return;
        modalShown = true;

        View view = LayoutInflater.from(this).inflate(R.layout.modal, null);
        TextView modalMessage = view.findViewById(R.id.modalMessage);
        Button cancelButton = view.findViewById(R.id.cancelButton);
        Button okayButton = view.findViewById(R.id.okayButton);

        modalMessage.setText("Are you sure you want to " + (isTimeIn ? "time in" : "time out") + " at:\n\n" + locationMessage + "?");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        cancelButton.setOnClickListener(v -> {
            modalShown = false;
            dialog.dismiss();
        });

        okayButton.setOnClickListener(v -> {
            modalShown = false;
            dialog.dismiss();
            if (isTimeIn) {
                proceedWithTimeAction(true, null);
            } else {
                showFeedbackDialog();
            }
        });

        dialog.show();
    }

    private void showFeedbackDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.activity_feedback_dialog, null);
        EditText feedbackEditText = dialogView.findViewById(R.id.feedbackEditText);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button submitButton = dialogView.findViewById(R.id.submitButton);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        submitButton.setOnClickListener(v -> {
            String feedback = feedbackEditText.getText().toString().trim();
            if (!feedback.isEmpty()) {
                dialog.dismiss();
                proceedWithTimeAction(false, feedback);
            } else {
                Toast.makeText(this, "Feedback cannot be empty!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void proceedWithTimeAction(boolean isTimeIn, String feedback) {
        Intent intent = new Intent(AttendanceRoom.this, isTimeIn ? ScanQRTimeIn.class : ScanQRTimeOut.class);
        intent.putExtra("uid", uid);
        intent.putExtra("roomId", roomId);
        intent.putExtra("latitude", latitude);
        intent.putExtra("longitude", longitude);
        intent.putExtra("location", location != null ? location : "Location not available");
        if (!isTimeIn) intent.putExtra("feedback", feedback);
        startActivity(intent);
    }

    private void checkAttendanceStatus(String roomId) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get today's date in "yyyyMMdd" format
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

        // Reference to today's attendance document
        DocumentReference attendanceRef = db.collection("rooms").document(roomId)
                .collection("students").document(uid)
                .collection("attendance").document(currentDate);

        attendanceRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                boolean hasTimeIn = documentSnapshot.contains("timeIn");
                boolean hasTimeOut = documentSnapshot.contains("timeOut");

                if (!hasTimeIn) {
                    // Time In is missing → Enable Time In, disable Time Out
                    timeInButton.setEnabled(true);
                    timeInButton.setAlpha(1.0f); // Full opacity

                    timeOutButton.setEnabled(false);
                    timeOutButton.setAlpha(0.5f); // Gray out
                } else if (!hasTimeOut) {
                    // Time Out is missing → Enable Time Out, disable Time In
                    timeInButton.setEnabled(false);
                    timeInButton.setAlpha(0.5f); // Gray out

                    timeOutButton.setEnabled(true);
                    timeOutButton.setAlpha(1.0f); // Full opacity
                } else {
                    // Both Time In & Time Out exist → Disable both
                    timeInButton.setEnabled(false);
                    timeInButton.setAlpha(0.5f); // Gray out

                    timeOutButton.setEnabled(false);
                    timeOutButton.setAlpha(0.5f); // Gray out
                }
            } else {
                // No attendance record → Allow only Time In
                timeInButton.setEnabled(true);
                timeInButton.setAlpha(1.0f); // Full opacity

                timeOutButton.setEnabled(false);
                timeOutButton.setAlpha(0.5f); // Gray out
            }
        }).addOnFailureListener(e -> Log.e("AttendanceStatus", "❌ Error checking attendance: ", e));
    }

}
