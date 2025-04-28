package com.example.smarttrack;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ScanQRTimeOut extends AppCompatActivity {
    private String uid;
    private String roomId;
    private static final float ALLOWED_RADIUS_METERS = 50.0f;
    private double userLongitude;
    private double userLatitude;
    private String location;
    private String feedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_qr);

        // Lock orientation to portrait
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        uid = getIntent().getStringExtra("uid");
        roomId = getIntent().getStringExtra("roomId");
        userLongitude = getIntent().getDoubleExtra("longitude", 0);
        userLatitude = getIntent().getDoubleExtra("latitude", 0);
        location = getIntent().getStringExtra("location");
        feedback = getIntent().getStringExtra("feedback");

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
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void handleQRScanResult(int requestCode, int resultCode, Intent data) {
        IntentResult qrResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (qrResult != null) {
            if (qrResult.getContents() == null) {
                Toast.makeText(this, "QR Scan canceled.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                validateTimeOutQR(qrResult.getContents());
            }
        }
    }

    private void validateTimeOutQR(String scannedData) {
        String todayDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("rooms")
                .document(roomId)
                .collection("dailyCodes")
                .document(todayDate)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String timeOutCode = documentSnapshot.getString("timeOutCode");
                        Double storedLat = documentSnapshot.getDouble("latitude");
                        Double storedLng = documentSnapshot.getDouble("longitude");

                        Log.d("ScanQRTimeOut", "Stored Time-Out Code: " + timeOutCode);
                        Log.d("ScanQRTimeOut", "Scanned Data: " + scannedData);

                        if (timeOutCode != null && timeOutCode.equals(scannedData)) {
                            boolean isNearby = storedLat != null && storedLng != null &&
                                    isWithinAllowedRadius(userLatitude, userLongitude, storedLat, storedLng);

                            if (isNearby) {
                                saveTimeOut(true);
                            } else {
                                showLocationMismatchModal();
                            }
                        } else {
                            Log.e("ScanQRTimeOut", "❌ Invalid Time-Out QR Code!");
                            Toast.makeText(this, "❌ Invalid Time-Out QR Code!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e("ScanQRTimeOut", "❌ No QR Code for Today!");
                        Toast.makeText(this, "❌ No QR Code for Today!", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> Log.e("ScanQRTimeOut", "❌ Firestore Error: " + e.getMessage()));

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
            saveTimeOut(false);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveTimeOut(boolean isSameLocation) {
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference roomRef = db.collection("rooms").document(roomId);

        roomRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists() && documentSnapshot.contains("endTime")) {
                Timestamp endTime = documentSnapshot.getTimestamp("endTime");
                Timestamp timeOut = Timestamp.now();

                String status = determineStatus(timeOut, endTime);

                Map<String, Object> attendanceData = new HashMap<>();
                attendanceData.put("timeOut", timeOut);
                attendanceData.put("isSameLocationTimeOut", isSameLocation);
                attendanceData.put("statusTimeOut", status);
                attendanceData.put("locationTimeOut", location);
                attendanceData.put("feedback", feedback);


                db.collection("rooms").document(roomId)
                        .collection("students").document(uid)
                        .collection("attendance").document(currentDate)
                        .update(attendanceData)
                        .addOnSuccessListener(aVoid -> {
                            debugMessage("✅ Time Out recorded with status: " + status);
                            goToHome();
                        })
                        .addOnFailureListener(e -> debugMessage("❌ Error: Time Out failed. " + e.getMessage()));

            } else {
                debugMessage("❌ Error: End time not found in room.");
            }
        }).addOnFailureListener(e -> debugMessage("❌ Error: Failed to retrieve room data. " + e.getMessage()));
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

    private void debugMessage(String message) {
        Log.d("DEBUG_LOG", message);
        showToast(message);
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(ScanQRTimeOut.this, message, Toast.LENGTH_SHORT).show());
    }

    private void goToHome() {
        startActivity(new Intent(ScanQRTimeOut.this, Students_Home.class));
        finish();
    }
}
