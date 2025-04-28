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
import android.os.Handler;
import android.os.Looper;
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

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class AttendanceEvent extends AppCompatActivity {
    private static final float ALLOWED_RADIUS_METERS = 2000.0f;
    private FirebaseFirestore db;
    private String uid, eventId, location;
    private Double longitude, latitude;
    private TextView toolbarTitle, subjectCodeField, sectionField, startTimeField, endTimeField;
    private TextView timeInField, locationTimeInField, statusTimeInField, locationField, teacherField;
    private TextView timeOutField, locationTimeOutField, statusTimeOutField, feedbackField;
    private Button timeInButton, timeOutButton;
    private LocationManager locationManager;
    private boolean modalShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        db = FirebaseFirestore.getInstance();
        eventId = getIntent().getStringExtra("eventId");
        uid = getIntent().getStringExtra("uid");
        location = getIntent().getStringExtra("location");
        longitude = getIntent().getDoubleExtra("longitude", 0);
        latitude = getIntent().getDoubleExtra("latitude", 0);

        toolbarTitle = findViewById(R.id.toolbarTitle);
        subjectCodeField = findViewById(R.id.subjectCodeField);
        sectionField = findViewById(R.id.sectionField);
        startTimeField = findViewById(R.id.startTimeField);
        endTimeField = findViewById(R.id.endTimeField);
        locationField = findViewById(R.id.locationField);
        teacherField = findViewById(R.id.teacherField);
        timeInField = findViewById(R.id.timeInField);
        locationTimeInField = findViewById(R.id.locationTimeInField);
        statusTimeInField = findViewById(R.id.statusTimeInField);
        timeOutField = findViewById(R.id.timeOutField);
        locationTimeOutField = findViewById(R.id.locationTimeOutField);
        statusTimeOutField = findViewById(R.id.statusTimeOutField);
        timeInButton = findViewById(R.id.timeInButton);
        timeOutButton = findViewById(R.id.timeOutButton);
        feedbackField = findViewById(R.id.feedbackField);

        fetchEventDetails();
        checkEventAttendanceStatus();
        fetchActiveEventAttendance();

        findViewById(R.id.backButton).setOnClickListener(v -> onBackPressed());

        timeInButton.setOnClickListener(v -> handleEventTimeAction(true));
        timeOutButton.setOnClickListener(v -> handleEventTimeAction(false));
    }

    private void fetchEventDetails() {
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        String description = documentSnapshot.getString("description");
                        String eventDate = documentSnapshot.getString("eventDate");
                        String location = documentSnapshot.getString("location");
                        String teacherId = documentSnapshot.getString("teacherId");

                        // Get latitude and longitude
                        latitude = documentSnapshot.getDouble("latitude");
                        longitude = documentSnapshot.getDouble("longitude");

                        // Handle cases where latitude/longitude might be null
                        if (latitude == null) latitude = 0.0;
                        if (longitude == null) longitude = 0.0;

                        toolbarTitle.setText(title);
                        subjectCodeField.setText(description);
                        sectionField.setText(eventDate);
                        locationField.setText(location);

                        Object startTimeObj = documentSnapshot.get("startTime");
                        Object endTimeObj = documentSnapshot.get("endTime");

                        startTimeField.setText("Start Time: " + formatTime(startTimeObj));
                        endTimeField.setText("End Time: " + formatTime(endTimeObj));

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

                        // You can update UI elements with these values
                    } else {
                        Log.d("EventDetails", "No such document");
                    }
                })
                .addOnFailureListener(e -> Log.e("EventDetails", "Error fetching event details", e));
    }

    private void fetchActiveEventAttendance() {
        if (eventId == null || uid == null) {
            Log.e("General", "Invalid event or user ID");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String todayDate = sdf.format(new Date());

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Step 1: Get all rooms in the event
        db.collection("events").document(eventId).collection("rooms")
                .get()
                .addOnSuccessListener(eventRooms -> {
                    for (QueryDocumentSnapshot roomDoc : eventRooms) {
                        String roomId = roomDoc.getId(); // Get room ID

                        // Step 2: Check if the student exists in main rooms collection
                        db.collection("rooms").document(roomId).collection("students").document(uid)
                                .get()
                                .addOnSuccessListener(studentDoc -> {
                                    if (studentDoc.exists()) {
                                        // Step 3: Fetch attendance record from event room
                                        db.collection("events").document(eventId)
                                                .collection("rooms").document(roomId)
                                                .collection("students").document(uid)
                                                .collection("attendance").document(todayDate)
                                                .get()
                                                .addOnSuccessListener(attendanceDoc -> {
                                                    if (attendanceDoc.exists()) {
                                                        // Time In Details
                                                        Object timeIn = attendanceDoc.get("timeIn");
                                                        String locationTimeIn = attendanceDoc.getString("locationTimeIn");
                                                        String statusTimeIn = attendanceDoc.getString("statusTimeIn");

                                                        // Update UI for Time In
                                                        timeInField.setText("Time In: " + formatTime(timeIn));
                                                        locationTimeInField.setText("Location: " + (locationTimeIn != null ? locationTimeIn : "N/A"));
                                                        statusTimeInField.setText("Status: " + (statusTimeIn != null ? statusTimeIn : "Absent"));

                                                        // Check if Time Out exists
                                                        if (attendanceDoc.contains("timeOut")) {
                                                            Object timeOut = attendanceDoc.get("timeOut");
                                                            String locationTimeOut = attendanceDoc.getString("locationTimeOut");
                                                            String statusTimeOut = attendanceDoc.getString("statusTimeOut");
                                                            String feedback = attendanceDoc.getString("feedback");

                                                            // Update UI for Time Out
                                                            timeOutField.setText("Time Out: " + formatTime(timeOut));
                                                            locationTimeOutField.setText("Location: " + (locationTimeOut != null ? locationTimeOut : "N/A"));
                                                            statusTimeOutField.setText("Status: " + (statusTimeOut != null ? statusTimeOut : "Not Timed Out"));
                                                            feedbackField.setText("Feedback: " + (feedback != null ? feedback : "No feedback provided"));
                                                        } else {
                                                            // Reset Time Out fields if no record exists
                                                            timeOutField.setText("Time Out: N/A");
                                                            locationTimeOutField.setText("Location: N/A");
                                                            statusTimeOutField.setText("Status: Not Timed Out");
                                                            feedbackField.setText("No feedback provided");
                                                        }
                                                    } else {
                                                        Log.d("Attendance", "No attendance record found for student in event room.");
                                                    }
                                                })
                                                .addOnFailureListener(e -> Log.e("General", "Error fetching attendance record", e));
                                    } else {
                                        Log.d("Student Check", "Student not found in main room: " + roomId);
                                    }
                                })
                                .addOnFailureListener(e -> Log.e("General", "Error checking student in main room", e));
                    }
                })
                .addOnFailureListener(e -> Log.e("General", "Error fetching event rooms", e));
    }



    private String formatFullName(String firstName, String middleName, String lastName) {
        return (firstName != null ? firstName + " " : "") +
                (middleName != null ? middleName + " " : "") +
                (lastName != null ? lastName : "").trim();
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

    private void checkEventAttendanceStatus() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get today's date in "yyyyMMdd" format
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

        db.collection("events").document(eventId)
                .collection("rooms")
                .get()
                .addOnSuccessListener(eventRoomsSnapshot -> {
                    if (eventRoomsSnapshot.isEmpty()) {
                        Log.d("AttendanceStatus", "❌ No rooms found for this event.");
                        return;
                    }

                    AtomicBoolean attendanceProcessed = new AtomicBoolean(false);

                    for (DocumentSnapshot eventRoomDoc : eventRoomsSnapshot.getDocuments()) {
                        String roomId = eventRoomDoc.getId();

                        DocumentReference attendanceRef = db.collection("events").document(eventId)
                                .collection("rooms").document(roomId)
                                .collection("students").document(uid)
                                .collection("attendance").document(currentDate);

                        attendanceRef.get().addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists() && !attendanceProcessed.get()) {
                                attendanceProcessed.set(true);

                                boolean hasTimeIn = documentSnapshot.contains("timeIn");
                                boolean hasTimeOut = documentSnapshot.contains("timeOut");

                                if (!hasTimeIn) {
                                    timeInButton.setEnabled(true);
                                    timeInButton.setAlpha(1.0f);
                                    timeOutButton.setEnabled(false);
                                    timeOutButton.setAlpha(0.5f);
                                } else if (!hasTimeOut) {
                                    timeInButton.setEnabled(false);
                                    timeInButton.setAlpha(0.5f);
                                    timeOutButton.setEnabled(true);
                                    timeOutButton.setAlpha(1.0f);
                                } else {
                                    timeInButton.setEnabled(false);
                                    timeInButton.setAlpha(0.5f);
                                    timeOutButton.setEnabled(false);
                                    timeOutButton.setAlpha(0.5f);
                                }
                            }
                        }).addOnFailureListener(e -> Log.e("AttendanceStatus", "❌ Error checking attendance: ", e));
                    }

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!attendanceProcessed.get()) {
                            timeInButton.setEnabled(true);
                            timeInButton.setAlpha(1.0f);
                            timeOutButton.setEnabled(false);
                            timeOutButton.setAlpha(0.5f);
                        }
                    }, 500);
                }).addOnFailureListener(e -> Log.e("AttendanceStatus", "❌ Error fetching event rooms: ", e));
    }

    private void handleEventTimeAction(boolean isTimeIn) {
        getCurrentEventLocation(isTimeIn);
    }

    private void getCurrentEventLocation(boolean isTimeIn) {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show();
            showEventConfirmationModal("Location not available", isTimeIn);
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                locationManager.removeUpdates(this);
                getEventAddressFromLocation(isTimeIn);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {
                showEventConfirmationModal("Location not available", isTimeIn);
            }
        });
    }

    private void getEventAddressFromLocation(boolean isTimeIn) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        new Thread(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                location = addresses.isEmpty() ? "Unknown Location" : addresses.get(0).getAddressLine(0);
            } catch (IOException e) {
                e.printStackTrace();
                location = "Unknown Location";
            }

            runOnUiThread(() -> showEventConfirmationModal(location, isTimeIn));
        }).start();
    }

    private void showEventConfirmationModal(String locationMessage, boolean isTimeIn) {
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
                proceedWithEventTimeAction(true, null);
            } else {
                showEventFeedbackDialog();
            }
        });

        dialog.show();
    }

    private void showEventFeedbackDialog() {
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
                proceedWithEventTimeAction(false, feedback);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Feedback cannot be empty!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void proceedWithEventTimeAction(boolean isTimeIn, String feedback) {
        if (isTimeIn) {
            Intent intent = new Intent(AttendanceEvent.this, FaceRecognition.class);
            intent.putExtra("eventId", eventId);
            intent.putExtra("uid", uid);
            intent.putExtra("latitude", latitude);
            intent.putExtra("longitude", longitude);
            intent.putExtra("location", location != null ? location : "Location not available");
            startActivity(intent);
        } else {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            // Get current date in yyyyMMdd format (e.g., "20250331")
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            String currentDate = dateFormat.format(new Date());

            Timestamp currentTimestamp = Timestamp.now();

            // Fetch event details (endTime, latitude, longitude)
            db.collection("events").document(eventId)
                    .get()
                    .addOnSuccessListener(eventDoc -> {
                        if (!eventDoc.exists()) {
                            Log.e("AttendanceEvent", "❌ Event not found.");
                            return;
                        }

                        String endTimeStr = eventDoc.getString("endTime");
                        double eventLat = eventDoc.getDouble("latitude"); // Get latitude
                        double eventLng = eventDoc.getDouble("longitude"); // Get longitude
                        Timestamp endTime = convertStringToTimestamp(endTimeStr);

                        // 🔹 Step 1: Find the room where the student exists
                        db.collection("events").document(eventId).collection("rooms")
                                .get()
                                .addOnSuccessListener(eventRoomsSnapshot -> {
                                    if (eventRoomsSnapshot.isEmpty()) {
                                        Log.e("AttendanceEvent", "❌ No rooms found for this event. Time Out failed.");
                                        return;
                                    }

                                    for (DocumentSnapshot eventRoomDoc : eventRoomsSnapshot.getDocuments()) {
                                        String roomId = eventRoomDoc.getId();

                                        // 🔹 Step 2: Check if student exists in this room
                                        db.collection("rooms").document(roomId)
                                                .collection("students").document(uid)
                                                .get()
                                                .addOnSuccessListener(studentDoc -> {
                                                    if (!studentDoc.exists()) {
                                                        Log.d("AttendanceEvent", "Student not found in room: " + roomId);
                                                        return;
                                                    }

                                                    // 🔹 Step 3: Reference to today's attendance document
                                                    DocumentReference attendanceRef = db.collection("events").document(eventId)
                                                            .collection("rooms").document(roomId)
                                                            .collection("students").document(uid)
                                                            .collection("attendance").document(currentDate);

                                                    attendanceRef.get()
                                                            .addOnSuccessListener(attendanceDoc -> {
                                                                if (!attendanceDoc.exists()) {
                                                                    Log.e("AttendanceEvent", "❌ No attendance record found for today.");
                                                                    Toast.makeText(AttendanceEvent.this, "No attendance record for today!", Toast.LENGTH_SHORT).show();
                                                                    return;
                                                                }

                                                                // Ensure lat/lng are not null before checking distance
                                                                boolean isSameLocation = isWithinAllowedRadius(eventLat, eventLng, latitude, longitude);

                                                                // 🔹 Step 5: Determine attendance status
                                                                String status = determineStatus(currentTimestamp, endTime);

                                                                // 🔹 Step 6: Update attendance with Time Out
                                                                Map<String, Object> timeOutData = new HashMap<>();
                                                                timeOutData.put("timeOut", currentTimestamp);
                                                                timeOutData.put("feedback", feedback);
                                                                timeOutData.put("statusTimeOut", status);
                                                                timeOutData.put("locationTimeOut", location);
                                                                timeOutData.put("isSameLocationTimeOut", isSameLocation);

                                                                attendanceRef.update(timeOutData)
                                                                        .addOnSuccessListener(aVoid -> {
                                                                            Log.d("AttendanceEvent", "✅ Time Out updated successfully. Status: " + status + ", isSameLocation: " + isSameLocation);
                                                                            Toast.makeText(AttendanceEvent.this, "Time Out recorded: " + status, Toast.LENGTH_SHORT).show();
                                                                        })
                                                                        .addOnFailureListener(e -> Log.e("AttendanceEvent", "❌ Error updating Time Out: " + e.getMessage()));
                                                            })
                                                            .addOnFailureListener(e -> Log.e("AttendanceEvent", "❌ Error fetching attendance record: " + e.getMessage()));
                                                })
                                                .addOnFailureListener(e -> Log.e("AttendanceEvent", "❌ Error checking student in room: " + e.getMessage()));
                                    }
                                })
                                .addOnFailureListener(e -> Log.e("AttendanceEvent", "❌ Error fetching event rooms: " + e.getMessage()));
                    })
                    .addOnFailureListener(e -> Log.e("AttendanceEvent", "❌ Error fetching event details: " + e.getMessage()));
        }
    }


    private Timestamp convertStringToTimestamp(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date date = sdf.parse(timeStr);

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, date.getHours());
            calendar.set(Calendar.MINUTE, date.getMinutes());
            calendar.set(Calendar.SECOND, 0);

            return new Timestamp(calendar.getTime());
        } catch (ParseException e) {
            Log.e("AttendanceEvent", "❌ Error parsing endTime: " + e.getMessage());
            return null;
        }
    }

    private boolean isWithinAllowedRadius(double userLat, double userLng, double storedLat, double storedLng) {
        Log.d("LocationCheck", "User coordinates - Latitude: " + userLat + ", Longitude: " + userLng);
        Log.d("LocationCheck", "Stored coordinates - Latitude: " + storedLat + ", Longitude: " + storedLng);

        float[] results = new float[1];
        Location.distanceBetween(userLat, userLng, storedLat, storedLng, results);

        // Optionally log the computed distance
        Log.d("LocationCheck", "Computed distance: " + results[0] + " meters");

        return results[0] <= ALLOWED_RADIUS_METERS;
    }

    private String determineStatus(Timestamp timeOut, Timestamp endTime) {
        Calendar endCal = Calendar.getInstance();
        endCal.setTime(endTime.toDate());

        Calendar timeOutCal = Calendar.getInstance();
        timeOutCal.setTime(timeOut.toDate());

        int endHour = endCal.get(Calendar.HOUR_OF_DAY);
        int endMinute = endCal.get(Calendar.MINUTE);

        int timeOutHour = timeOutCal.get(Calendar.HOUR_OF_DAY);
        int timeOutMinute = timeOutCal.get(Calendar.MINUTE);

        if (timeOutHour > endHour || (timeOutHour == endHour && timeOutMinute >= endMinute)) {
            return "On Time";
        } else {
            return "Left Early";
        }
    }


}
