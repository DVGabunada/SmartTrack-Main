package com.example.smarttrack;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Objects;

public class InputCodeActivity extends AppCompatActivity {

    private EditText editTextRoomCode;
    private Button buttonSubmitCode;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_input_code);

        editTextRoomCode = findViewById(R.id.editTextRoomCode);
        buttonSubmitCode = findViewById(R.id.buttonSubmitCode);
        firestore = FirebaseFirestore.getInstance();

        buttonSubmitCode.setOnClickListener(v -> {
            String roomCode = editTextRoomCode.getText().toString().trim();
            if (!roomCode.isEmpty()) {
                verifyRoomCode(roomCode);
            } else {
                Toast.makeText(InputCodeActivity.this, "Please enter a room code.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyRoomCode(String roomCode) {
        firestore.collection("rooms")
                .whereEqualTo("roomCode", roomCode)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !Objects.requireNonNull(task.getResult()).isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        String roomId = document.getId();
                        String status = document.getString("status");

                        if ("closed".equals(status)) {
                            Toast.makeText(InputCodeActivity.this, "The QR code is closed. Please contact your teacher.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String subjectCode = document.getString("subjectCode");
                        String section = document.getString("section");
                        String teacherId = document.getString("teacherId");
                        String adminId = document.getString("adminId");
                        int maxStudents = Integer.parseInt(Objects.requireNonNull(document.getString("numberOfStudents")));

                        checkRoomCapacityAndSaveStudent(roomId, subjectCode, section, teacherId, adminId, maxStudents);
                    } else {
                        Toast.makeText(InputCodeActivity.this, "Invalid room code. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(InputCodeActivity.this, "Error fetching room details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void checkRoomCapacityAndSaveStudent(String roomId, String subjectCode, String section, String teacherId, String adminId, int maxStudents) {
        String studentId = FirebaseAuth.getInstance().getUid();

        if (studentId == null) {
            Toast.makeText(this, "You are not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if student already enrolled in the room
        firestore.collection("rooms").document(roomId)
                .collection("students").document(studentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Student is already enrolled
                        Toast.makeText(InputCodeActivity.this, "You already enrolled in this room.", Toast.LENGTH_SHORT).show();
                    } else {
                        // Student is not enrolled yet, check room capacity
                        firestore.collection("rooms").document(roomId)
                                .collection("students")
                                .get()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        int currentStudents = Objects.requireNonNull(task.getResult()).size();

                                        if (currentStudents < maxStudents) {
                                            saveStudentToRoom(roomId, subjectCode, section, teacherId, adminId);
                                        } else {
                                            Toast.makeText(InputCodeActivity.this, "Room is full. Maximum capacity reached.", Toast.LENGTH_SHORT).show();
                                        }
                                    } else {
                                        Toast.makeText(InputCodeActivity.this, "Error checking room capacity: " + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(InputCodeActivity.this, "Error checking enrollment status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveStudentToRoom(String roomId, String subjectCode, String section, String teacherId, String adminId) {
        String studentId = FirebaseAuth.getInstance().getUid();

        if (studentId != null) {
            // Add the student data into the 'students' subcollection
            firestore.collection("rooms").document(roomId)
                    .collection("students").document(studentId)
                    .set(new StudentModel(studentId, roomId, subjectCode, section, teacherId))
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(InputCodeActivity.this, "Successfully joined the room.", Toast.LENGTH_SHORT).show();
                        navigateToStudentsRoom(roomId, teacherId, section, subjectCode, studentId, adminId);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(InputCodeActivity.this, "Error saving student data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            Toast.makeText(this, "You are not logged in.", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToStudentsRoom(String roomId, String teacherId, String section, String subjectCode, String studentId, String adminId) {
        Log.d("InputCodeActivity", "Navigating to Students_Room with roomId: " + roomId);

        if (teacherId != null) {
            Toast.makeText(this, "Invalid student number format. Please contact your teacher.", Toast.LENGTH_SHORT).show();
        } else if (adminId != null) {
            Toast.makeText(this, "Invalid student number format. Please contact your admin.", Toast.LENGTH_SHORT).show();
        }

        Intent intent = new Intent(InputCodeActivity.this, Students_Room.class);
        intent.putExtra("roomId", roomId);
        intent.putExtra("teacherId", teacherId);
        intent.putExtra("section", section);
        intent.putExtra("subjectCode", subjectCode);
        intent.putExtra("studentId", studentId);
        startActivity(intent);
        finish();
    }
}
