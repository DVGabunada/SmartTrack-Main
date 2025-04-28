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

public class ViewAttendance extends AppCompatActivity {
    private static final String TAG = "ViewAttendance";
    private FirebaseFirestore db;
    private LinearLayout containerLayout;
    private TextView title;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_students);

        db = FirebaseFirestore.getInstance();
        containerLayout = findViewById(R.id.containerLayout);

        title = findViewById(R.id.toolbarTitle);

        title.setText("Attendance");

        String roomId = getIntent().getStringExtra("roomId");

        Log.d(TAG, "ViewRoomStudents: Received roomId = " + roomId);

        if (roomId == null || roomId.isEmpty()) {
            Toast.makeText(this, "Room ID is missing!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "ViewRoomStudents: Room ID is NULL or EMPTY!");
            finish();
            return;
        }

        fetchStudentsInRoom(roomId);
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());
    }

    private void fetchStudentsInRoom(String roomId) {
        Log.d(TAG, "🔍 Fetching students in room ID: " + roomId);

        db.collection("rooms")
                .document(roomId)
                .collection("students")
                .get()
                .addOnSuccessListener(studentSnapshots -> {
                    Log.d(TAG, "📂 Retrieved student documents count: " + studentSnapshots.size());
                    if (!studentSnapshots.isEmpty()) {
                        for (DocumentSnapshot studentDoc : studentSnapshots) {
                            String studentId = studentDoc.getId();
                            Log.d(TAG, "✅ Found student ID in room: " + studentId);
                            fetchStudentDetails(roomId, studentId);
                        }
                    } else {
                        Log.d(TAG, "🛑 No student documents found for room ID: " + roomId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🚨 Error fetching students for room ID: " + roomId, e);
                });
    }

    private void fetchStudentDetails(String roomId, String studentId) {
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

                        fetchAttendanceForStudent(roomId, studentId, idNumber, name);
                    } else {
                        Log.d(TAG, "🛑 No student details found for ID: " + studentId);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "🚨 Error fetching student details: " + studentId, e));
    }

    private void fetchAttendanceForStudent(String roomId, String studentId, String idNumber, String name) {
        Log.d(TAG, "🔍 Fetching today's attendance for student ID: " + studentId);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String todayId = sdf.format(new Date());

        db.collection("rooms")
                .document(roomId)
                .collection("students")
                .document(studentId)
                .collection("attendance")
                .document(todayId)
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
        View studentCardView = getLayoutInflater().inflate(R.layout.home_card, containerLayout, false);

        TextView nameTextView = studentCardView.findViewById(R.id.title);
        TextView idNumberTextView = studentCardView.findViewById(R.id.subtitle);
        TextView contentTextView = studentCardView.findViewById(R.id.content);

        idNumberTextView.setText(idNumber != null ? idNumber : "N/A");
        nameTextView.setText(name != null ? name : "N/A");

        String timeText = "Time In: " + (timeIn != null ? timeIn : "N/A") +
                " | Time Out: " + (timeOut != null ? timeOut : "N/A");
        contentTextView.setText(timeText);

        containerLayout.addView(studentCardView);
    }
}