package com.example.smarttrack;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class StudentView extends AppCompatActivity {

    private static final String TAG = "StudentView";
    private FirebaseFirestore db;
    private TableLayout tableLayout;
    private LinearLayout containerLayout;
    private String roomId;
    private int studentCounter = 1;
    private Map<Integer, String> selectedStudents = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_students);

        db = FirebaseFirestore.getInstance();
        containerLayout = findViewById(R.id.containerLayout);
        tableLayout = findViewById(R.id.studentsTable);

        roomId = getIntent().getStringExtra("roomId");

        if (roomId == null || roomId.isEmpty()) {
            Log.e(TAG, "StudentView: Received NULL roomId");
            Toast.makeText(this, "Error: Room ID is missing!", Toast.LENGTH_LONG).show();
            finish(); // 🚨 Close the activity to prevent further errors
            return;
        }

        Log.d(TAG, "StudentView: Successfully received roomId = " + roomId);

        fetchTeacherDetails(roomId);
        fetchStudentsFromRoom(roomId);
    }

    private void fetchTeacherDetails(String roomId) {
        Log.d(TAG, "Fetching teacher and room details for room ID: " + roomId);

        db.collection("rooms")
                .document(roomId)
                .get()
                .addOnSuccessListener(roomDoc -> {
                    if (roomDoc.exists()) {
                        // Fetch teacher details
                        String teacherId = roomDoc.getString("teacherId");
                        String adminId = roomDoc.getString("adminId");

                        if (teacherId != null) {
                            fetchTeacherName(teacherId);
                        } else if (adminId != null) {
                            fetchAdminName(adminId); // 🆕 Fetch admin name if teacherId is missing
                        } else {
                            addTeacherNameToUI("Unknown Teacher");
                        }

                        // Fetch room details
                        String subjectName = roomDoc.getString("subjectName");
                        String subjectCode = roomDoc.getString("subjectCode");
                        String section = roomDoc.getString("section");
                        String numberOfStudents = roomDoc.getString("numberOfStudents");

                        // Fetch timestamps and format them
                        Timestamp startTimeStamp = roomDoc.getTimestamp("startTime");
                        Timestamp endTimeStamp = roomDoc.getTimestamp("endTime");

                        String startTime = formatTimestamp(startTimeStamp);
                        String endTime = formatTimestamp(endTimeStamp);

                        // Display room details in UI
                        addRoomDetailsToUI(startTime, endTime, subjectName, subjectCode, section, numberOfStudents);
                    } else {
                        Log.e(TAG, "Room document not found for ID: " + roomId);
                        addRoomDetailsToUI("N/A", "N/A", "N/A", "N/A", "N/A", "N/A");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching room details for room ID: " + roomId, e);
                    addRoomDetailsToUI("Error", "Error", "Error", "Error", "Error", "Error");
                });
    }


    private void fetchAdminName(String adminId) {
        Log.d(TAG, "Fetching admin details for Admin ID: " + adminId);

        db.collection("administrator")
                .document(adminId)
                .get()
                .addOnSuccessListener(adminDoc -> {
                    if (adminDoc.exists()) {
                        String firstName = adminDoc.getString("firstName");
                        String lastName = adminDoc.getString("lastName");

                        String fullName = firstName + " " + lastName;

                        Log.d(TAG, "Admin Name: " + fullName);
                        addTeacherNameToUI("Admin: " + fullName);  // 🆕 Display admin's name in the UI
                    } else {
                        Log.e(TAG, "Admin document not found for ID: " + adminId);
                        addTeacherNameToUI("Unknown Admin");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching admin details for ID: " + adminId, e);
                    addTeacherNameToUI("Error fetching admin details");
                });
    }



    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp.getSeconds() * 1000)); // Convert seconds to milliseconds
    }

    private void addRoomDetailsToUI(String startTime, String endTime, String subjectName, String subjectCode, String section, String numberOfStudents) {
        TextView roomDetailsTextView = new TextView(this);
        roomDetailsTextView.setText(
                "Start Time: " + startTime + " - " +
                        "End Time: " + endTime + "\n" +
                        subjectName + " : " + subjectCode + " - " + section + "\n" +
                        "Student Capacity: " + numberOfStudents
        );
        roomDetailsTextView.setPadding(8, 20, 8, 20);
        roomDetailsTextView.setGravity(Gravity.START);
        roomDetailsTextView.setTextSize(16);
        roomDetailsTextView.setTypeface(null, android.graphics.Typeface.BOLD);

        // Add room details ABOVE the students table
        containerLayout.addView(roomDetailsTextView, containerLayout.indexOfChild(tableLayout));
    }



    private void fetchTeacherName(String teacherId) {
        Log.d(TAG, "Fetching teacher details for Teacher ID: " + teacherId);

        db.collection("teachers")
                .document(teacherId)
                .get()
                .addOnSuccessListener(teacherDoc -> {
                    if (teacherDoc.exists()) {
                        String firstName = teacherDoc.getString("firstName");
                        String middleName = teacherDoc.getString("middleName");
                        String lastName = teacherDoc.getString("lastName");

                        String fullName = firstName + " " +
                                (middleName != null ? middleName + " " : "") + lastName;

                        Log.d(TAG, "Teacher Name: " + fullName);
                        addTeacherNameToUI(fullName);
                    } else {
                        Log.e(TAG, "Teacher document not found for ID: " + teacherId);
                        addTeacherNameToUI("Unknown Teacher");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching teacher details for ID: " + teacherId, e);
                    addTeacherNameToUI("Error fetching teacher details");
                });
    }

    private void addTeacherNameToUI(String teacherName) {
        TextView teacherNameTextView = new TextView(this);
        teacherNameTextView.setText("Teacher: " + teacherName);
        teacherNameTextView.setPadding(8, 50, 8, 50);
        teacherNameTextView.setGravity(Gravity.CENTER);
        teacherNameTextView.setTextSize(18);
        teacherNameTextView.setTypeface(null, android.graphics.Typeface.BOLD);

        containerLayout.addView(teacherNameTextView, 0); // Add teacher name at the top
    }

    private void fetchStudentsFromRoom(String roomId) {
        String roomPath = "rooms/" + roomId + "/students";
        Log.d(TAG, "Fetching students from path: " + roomPath);

        db.collection(roomPath)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot studentDoc : queryDocumentSnapshots) {
                            String studentId = studentDoc.getId();

                            if (studentId != null) {
                                fetchStudentDetails(studentId);
                            } else {
                                Log.e(TAG, "Missing student ID for a document in room: " + roomId);
                            }
                        }
                    } else {
                        Toast.makeText(this, "No students found in this room.", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "No students found for room ID: " + roomId);
                        addEmptyRowToTable("No students found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching students from room: " + roomId, e);
                    Toast.makeText(this, "Error fetching students: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchStudentDetails(String studentId) {
        Log.d(TAG, "Fetching details for Student ID: " + studentId);

        db.collection("students")
                .document(studentId)
                .get()
                .addOnSuccessListener(studentSnapshot -> {
                    if (studentSnapshot.exists()) {
                        String idNumber = studentSnapshot.getString("idNumber");
                        String firstName = studentSnapshot.getString("firstName");
                        String middleName = studentSnapshot.getString("middleName");
                        String lastName = studentSnapshot.getString("lastName");

                        String fullName = firstName + " " +
                                (middleName != null ? middleName + " " : "") + lastName;

                        addStudentRowToTable(studentId, idNumber, fullName);
                    } else {
                        Log.e(TAG, "Student document not found for ID: " + studentId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching student details for ID: " + studentId, e);
                    Toast.makeText(this, "Error fetching student details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void addStudentRowToTable(String studentId, String idNumber, String fullName) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));


        // Add Counter to "No." Column
        TextView counterTextView = new TextView(this);
        counterTextView.setText(String.valueOf(studentCounter)); // Display the current counter
        counterTextView.setPadding(8, 8, 8, 8);
        counterTextView.setGravity(Gravity.CENTER);
        row.addView(counterTextView);

        // Add ID Number column
        TextView idNumberTextView = new TextView(this);
        idNumberTextView.setText(idNumber);
        idNumberTextView.setPadding(8, 8, 8, 8);
        idNumberTextView.setGravity(Gravity.CENTER);
        row.addView(idNumberTextView);

        // Add Full Name column
        TextView nameTextView = new TextView(this);
        nameTextView.setText(fullName);
        nameTextView.setPadding(8, 8, 8, 8);
        nameTextView.setGravity(Gravity.CENTER);
        row.addView(nameTextView);

        ImageView menuIcon = new ImageView(this);
        menuIcon.setImageResource(android.R.drawable.ic_menu_more);
        menuIcon.setVisibility(View.GONE);
        row.addView(menuIcon);


        tableLayout.addView(row);
        studentCounter++; // Increment the counter for the next student
    }




    private void addEmptyRowToTable(String message) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        TextView messageTextView = new TextView(this);
        messageTextView.setText(message);
        messageTextView.setPadding(8, 8, 8, 8);
        messageTextView.setGravity(Gravity.CENTER);
        row.addView(messageTextView);

        tableLayout.addView(row);
    }
}
