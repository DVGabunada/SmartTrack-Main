package com.example.smarttrack;

import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ViewEventStudents extends AppCompatActivity {

    private static final String TAG = "ViewEventStudents";
    private FirebaseFirestore db;
    private LinearLayout containerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_students);

        db = FirebaseFirestore.getInstance();
        containerLayout = findViewById(R.id.containerLayout);

        String eventId = getIntent().getStringExtra("eventId");

        Log.d(TAG, "ViewEventStudents: Received eventId = " + eventId);

        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Event ID is missing!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "ViewEventStudents: Event ID is NULL or EMPTY!");
            finish();
            return;
        }

        fetchRoomsForEvent(eventId);
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());
    }

    private void fetchRoomsForEvent(String eventId) {
        Log.d(TAG, "Fetching rooms for event ID: " + eventId);

        db.collection("events")
                .document(eventId)
                .collection("rooms")
                .get()
                .addOnSuccessListener(roomSnapshots -> {
                    if (!roomSnapshots.isEmpty()) {
                        for (DocumentSnapshot roomDoc : roomSnapshots) {
                            String roomId = roomDoc.getId();
                            Log.d(TAG, "✅ Found room ID: " + roomId);
                            fetchStudentsInRoom(eventId, roomId);
                        }
                    } else {
                        Log.d(TAG, "🛑 No rooms found for event ID: " + eventId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🚨 Error fetching rooms for event ID: " + eventId, e);
                    Toast.makeText(this, "Error fetching rooms: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchStudentsInRoom(String eventId, String roomId) {
        Log.d(TAG, "🔍 Fetching students in room ID: " + roomId);

        db.collection("events")
                .document(eventId)
                .collection("rooms")
                .document(roomId)
                .collection("students")
                .get()
                .addOnSuccessListener(studentSnapshots -> {
                    Log.d(TAG, "📂 Retrieved student documents count: " + studentSnapshots.size());
                    if (!studentSnapshots.isEmpty()) {
                        for (DocumentSnapshot studentDoc : studentSnapshots) {
                            String studentId = studentDoc.getId();
                            Log.d(TAG, "✅ Found student ID in room: " + studentId);
                            fetchStudentDetails(eventId, roomId, studentId);
                        }
                    } else {
                        Log.d(TAG, "🛑 No student documents found for room ID: " + roomId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🚨 Error fetching students for room ID: " + roomId, e);
                });
    }

    private void fetchStudentDetails(String eventId, String roomId, String studentId) {
        Log.d(TAG, "🔍 Fetching student details from root collection for ID: " + studentId);

        db.collection("students")
                .document(studentId)
                .get()
                .addOnSuccessListener(studentDoc -> {
                    if (studentDoc.exists()) {
                        String idNumber = studentDoc.getString("idNumber");
                        String lastname = studentDoc.getString("lastName");
                        String firstname = studentDoc.getString("firstName");
                        String name = lastname + ", " + firstname;

                        Log.d(TAG, "✅ Student details found: ID Number = " + idNumber + " | Name = " + name);

                        fetchAttendanceForStudent(eventId, roomId, studentId, idNumber, name);
                    } else {
                        Log.d(TAG, "🛑 No student details found for ID: " + studentId);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "🚨 Error fetching student details: " + studentId, e));
    }


    private void fetchAttendanceForStudent(String eventId, String roomId, String studentId, String idNumber, String name) {
        Log.d(TAG, "🔍 Fetching today's attendance for student ID: " + studentId);

        // Get today's date in yyyyMMdd format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String todayId = sdf.format(new Date());

        db.collection("events")
                .document(eventId)
                .collection("rooms")
                .document(roomId)
                .collection("students")
                .document(studentId)
                .collection("attendance")
                .document(todayId) // Directly access today's document
                .get()
                .addOnSuccessListener(attendanceDoc -> {
                    if (attendanceDoc.exists()) {
                        Timestamp timeInTimestamp = attendanceDoc.getTimestamp("timeIn");
                        Timestamp timeOutTimestamp = attendanceDoc.getTimestamp("timeOut");
                        String status = attendanceDoc.getString("status");

                        String timeIn = (timeInTimestamp != null) ? timeInTimestamp.toDate().toString() : "N/A";
                        String timeOut = (timeOutTimestamp != null) ? timeOutTimestamp.toDate().toString() : "N/A";

                        Log.d(TAG, "📅 Attendance found: " + todayId +
                                " | Status: " + status + " | Time In: " + timeIn + " | Time Out: " + timeOut);

                        // Add student details with today's attendance info
                        addStudentCardToLayout(idNumber, name, timeIn, timeOut);
                    } else {
                        Log.d(TAG, "🛑 No attendance record for today (" + todayId + ") for student ID: " + studentId);
                        addStudentCardToLayout(idNumber, name, "N/A", "N/A");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🚨 Error fetching today's attendance for student ID: " + studentId, e);
                });
    }



    private void addStudentCardToLayout(String idNumber, String name, String timeIn, String timeOut) {
        // Inflate the card layout
        View studentCardView = getLayoutInflater().inflate(R.layout.home_card, containerLayout, false);

        // Find views inside the card
        TextView idNumberTextView = studentCardView.findViewById(R.id.title);
        TextView nameTextView = studentCardView.findViewById(R.id.subtitle);
        TextView contentTextView = studentCardView.findViewById(R.id.content); // Time in/out field

        // Set text values
        idNumberTextView.setText(idNumber != null ? idNumber : "N/A");
        nameTextView.setText(name != null ? name : "N/A");

        // Set time in and time out
        String timeText = "Time In: " + (timeIn != null ? timeIn : "N/A") +
                " | Time Out: " + (timeOut != null ? timeOut : "N/A");
        contentTextView.setText(timeText);

        // Add the card view to the container layout
        containerLayout.addView(studentCardView);
    }

}