package com.example.smarttrack;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.Objects;

public class ScanQRActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private static final String TAG = "ScanQRActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        firestore = FirebaseFirestore.getInstance();

        // 🔥 Check for Camera Permission before scanning
        checkCameraPermission();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startQRScanner();
        }
    }

    private void startQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan the room QR code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true); // Keeps scanner in portrait mode
        options.setBarcodeImageEnabled(true); // Ensures barcode image is captured
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE); // Ensures only QR codes are scanned
        options.setCaptureActivity(CustomCaptureActivity.class); // Uses custom capture activity

        qrCodeLauncher.launch(options);
    }

    private final ActivityResultLauncher<ScanOptions> qrCodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String scannedRoomCode = result.getContents();
                    Log.d(TAG, "Scanned Room Code: " + scannedRoomCode);
                    verifyRoomCode(scannedRoomCode);
                } else {
                    Toast.makeText(this, "QR scan canceled", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    private void verifyRoomCode(String roomCode) {
        firestore.collection("rooms")
                .whereEqualTo("roomCode", roomCode)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !Objects.requireNonNull(task.getResult()).isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);

                        String roomId = document.getId(); // This is guaranteed to be non-null
                        String subjectCode = document.getString("subjectCode");
                        String section = document.getString("section");
                        String teacherId = document.getString("teacherId");
                        String adminId = document.getString("adminId");
                        String numberOfStudentsStr = document.getString("numberOfStudents");
                        String status = document.getString("status");

// 👇 Check if status is "closed"
                        if ("closed".equalsIgnoreCase(status)) {
                            if (adminId != null) {
                                Toast.makeText(this, "Invalid room data. Please contact your admin.", Toast.LENGTH_SHORT).show();
                            } else if (teacherId != null) {
                                Toast.makeText(this, "Invalid room data. Please contact your teacher.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Invalid room data. Please contact support.", Toast.LENGTH_SHORT).show();
                            }
                            finish();
                            return;
                        }

                        try {
                            int maxStudents = Integer.parseInt(numberOfStudentsStr);
                            checkRoomCapacityAndSaveStudent(roomId, subjectCode, section, teacherId, maxStudents);
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid student number format. Please contact your teacher.", Toast.LENGTH_SHORT).show();
                            Log.e("ScanQRActivity", "Error parsing numberOfStudents: " + e.getMessage());
                            finish();
                        }
                    } else {
                        Toast.makeText(this, "Invalid room QR code. Please try again.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching room details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }


    private void checkRoomCapacityAndSaveStudent(String roomId, String subjectCode, String section, String teacherId, int maxStudents) {
        String studentId = FirebaseAuth.getInstance().getUid();

        if (studentId == null) {
            Toast.makeText(this, "You are not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        firestore.collection("rooms").document(roomId)
                .collection("students").document(studentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Toast.makeText(this, "You are already enrolled in this room.", Toast.LENGTH_SHORT).show();
                        navigateToStudentsRoom(roomId, teacherId, section, subjectCode, studentId);
                    } else {
                        firestore.collection("rooms").document(roomId)
                                .collection("students")
                                .get()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        int currentStudents = Objects.requireNonNull(task.getResult()).size();

                                        if (currentStudents < maxStudents) {
                                            saveStudentToRoom(roomId, subjectCode, section, teacherId);
                                        } else {
                                            Toast.makeText(this, "Room is full. Maximum capacity reached.", Toast.LENGTH_SHORT).show();
                                            finish();
                                        }
                                    } else {
                                        Toast.makeText(this, "Error checking room capacity: " + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_SHORT).show();
                                        finish();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error checking enrollment status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void saveStudentToRoom(String roomId, String subjectCode, String section, String teacherId) {
        String studentId = FirebaseAuth.getInstance().getUid();

        if (studentId != null) {
            firestore.collection("rooms").document(roomId)
                    .collection("students").document(studentId)
                    .set(new StudentModel(studentId, roomId, subjectCode, section, teacherId))
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Successfully joined the room.", Toast.LENGTH_SHORT).show();
                        navigateToStudentsRoom(roomId, teacherId, section, subjectCode, studentId);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error saving student data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    });
        } else {
            Toast.makeText(this, "You are not logged in.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void navigateToStudentsRoom(String roomId, String teacherId, String section, String subjectCode, String studentId) {
        Log.d(TAG, "Navigating to Students_Room with roomId: " + roomId);

        Intent intent = new Intent(ScanQRActivity.this, Students_Room.class);
        intent.putExtra("roomId", roomId);
        intent.putExtra("teacherId", teacherId);
        intent.putExtra("section", section);
        intent.putExtra("subjectCode", subjectCode);
        intent.putExtra("studentId", studentId);
        startActivity(intent);
        finish();
    }
}
