package com.example.smarttrack;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScanQRTimeIn extends AppCompatActivity {
    private String uid;
    private String roomId;
    private static final float ALLOWED_RADIUS_METERS = 50.0f;
    private double userLongitude;
    private double userLatitude;
    private String location;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_qr);

        // Lock orientation to portrait
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        uid = getIntent().getStringExtra("uid");
        roomId = getIntent().getStringExtra("roomId");
        userLongitude = getIntent().getDoubleExtra("longitude", 0);
        userLatitude = getIntent().getDoubleExtra("latitude",0);
        location = getIntent().getStringExtra("location");

        // Start QR Scanner
        new IntentIntegrator(this)
                .setCaptureActivity(CustomCaptureActivity.class)
                .setOrientationLocked(true)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setBeepEnabled(true)
                .setPrompt("Scan a QR code")
                .initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        handleQRScanResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data); // Pass unhandled cases to the superclass
    }

    private void handleQRScanResult(int requestCode, int resultCode, Intent data) {
        IntentResult qrResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (qrResult != null) {
            if (qrResult.getContents() == null) {
                Toast.makeText(this, "QR Scan canceled.", Toast.LENGTH_SHORT).show();
                finish(); // Close activity
            } else {
                validateTimeInQR(qrResult.getContents());
            }
        }
    }

    private void validateTimeInQR(String scannedData) {
        String todayDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("rooms")
                .document(roomId)
                .collection("dailyCodes")
                .document(todayDate)  // Access the document directly
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String timeInCode = documentSnapshot.getString("timeInCode");
                        Double storedLat = documentSnapshot.getDouble("latitude");
                        Double storedLng = documentSnapshot.getDouble("longitude");

                        Log.d("ScanQRTimeIn", "Stored Time-In Code: " + timeInCode);
                        Log.d("ScanQRTimeIn", "Scanned Data: " + scannedData);

                        if (timeInCode != null && timeInCode.equals(scannedData)) {
                            boolean isNearby = storedLat != null && storedLng != null &&
                                    isWithinAllowedRadius(userLatitude, userLongitude, storedLat, storedLng);

                            if (isNearby) {
                                saveTimeIn(true); // ✅ Time-In Success
                            } else {
                                showLocationMismatchModal(); // ⚠️ Wrong location
                            }
                        } else {
                            Log.e("ScanQRTimeIn", "❌ Invalid Time-In QR Code!");
                            Toast.makeText(this, "❌ Invalid Time-In QR Code!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e("ScanQRTimeIn", "❌ No QR Code for Today!");
                        Toast.makeText(this, "❌ No QR Code for Today!", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> Log.e("ScanQRTimeIn", "❌ Firestore Error: " + e.getMessage()));

    }

    private boolean isWithinAllowedRadius(double userLat, double userLng, double storedLat, double storedLng) {
        float[] results = new float[1];
        Location.distanceBetween(userLat, userLng, storedLat, storedLng, results);
        return results[0] <= ALLOWED_RADIUS_METERS;
    }

    private void showLocationMismatchModal() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.modal, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        TextView modalTitle = dialogView.findViewById(R.id.modalTitle);
        TextView modalMessage = dialogView.findViewById(R.id.modalMessage);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button okayButton = dialogView.findViewById(R.id.okayButton);

        modalTitle.setText("Location Mismatch");
        modalMessage.setText("You are not in the required location.\nDo you want to proceed?");

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        okayButton.setOnClickListener(v -> {
            saveTimeIn(false); // Save with isSameLocation = false
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveTimeIn(boolean isSameLocation) {
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference roomRef = db.collection("rooms").document(roomId);

        roomRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists() && documentSnapshot.contains("startTime")) {
                Timestamp startTime = documentSnapshot.getTimestamp("startTime");
                Timestamp timeIn = Timestamp.now(); // Get the current time

                String status = determineStatus(timeIn, startTime);

                Map<String, Object> attendanceData = new HashMap<>();
                attendanceData.put("timeIn", timeIn);
                attendanceData.put("isSameLocationTimeIn", isSameLocation);
                attendanceData.put("statusTimeIn", status);
                attendanceData.put("locationTimeIn", location);

                db.collection("rooms").document(roomId)
                        .collection("students").document(uid)
                        .collection("attendance").document(currentDate)
                        .set(attendanceData)
                        .addOnSuccessListener(aVoid -> {
                            debugMessage("✅ Time In recorded with status: " + status);
                            goToHome();
                        })
                        .addOnFailureListener(e -> debugMessage("❌ Error: Time In failed. " + e.getMessage()));

            } else {
                debugMessage("❌ Error: Start time not found in room.");
            }
        }).addOnFailureListener(e -> debugMessage("❌ Error: Failed to retrieve room data. " + e.getMessage()));
    }

    private String determineStatus(Timestamp timeIn, Timestamp startTime) {
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(startTime.toDate());

        Calendar timeInCal = Calendar.getInstance();
        timeInCal.setTime(timeIn.toDate());

        // Compare only HOUR and MINUTE
        int startHour = startCal.get(Calendar.HOUR_OF_DAY);
        int startMinute = startCal.get(Calendar.MINUTE);

        int timeInHour = timeInCal.get(Calendar.HOUR_OF_DAY);
        int timeInMinute = timeInCal.get(Calendar.MINUTE);

        if (timeInHour < startHour || (timeInHour == startHour && timeInMinute <= startMinute)) {
            return "On Time"; // Time in is earlier or exactly at the start time
        } else {
            return "Late"; // Time in is after the start time
        }
    }

    private void debugMessage(String message) {
        Log.d("DEBUG_LOG", message); // ✅ Logs message to Logcat
        showToast(message);
    }


    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(ScanQRTimeIn.this, message, Toast.LENGTH_SHORT).show());
    }

    private void goToHome() {
        startActivity(new Intent(ScanQRTimeIn.this, Students_Home.class));
        finish();
    }

}
