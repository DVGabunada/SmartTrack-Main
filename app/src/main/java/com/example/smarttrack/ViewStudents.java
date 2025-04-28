package com.example.smarttrack;

import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
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
import java.util.Locale;
import java.util.Date;


public class ViewStudents extends AppCompatActivity {

    private static final String TAG = "ViewStudents";
    private FirebaseFirestore db;
    private TableLayout tableLayout;
    private LinearLayout containerLayout;
    private int studentCounter = 1;
    private TextView teacherNameTextView; // Store reference to the teacher name TextView
    private TextView adminNameTextView; // Store reference to the admin name TextView



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_students);

        db = FirebaseFirestore.getInstance();
        containerLayout = findViewById(R.id.containerLayout);
        tableLayout = findViewById(R.id.studentsTable);

        String roomId = getIntent().getStringExtra("roomId");

        Log.d(TAG, "ViewStudents: Received roomId = " + roomId);

        if (roomId == null || roomId.isEmpty()) {
            Toast.makeText(this, "Room ID is missing!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "ViewStudents: Room ID is NULL or EMPTY!");
            finish();
            return;
        }

        fetchTeacherDetails(roomId);
        fetchStudentsFromRoom(roomId);
    }


    private void fetchTeacherDetails(String roomId) {
        Log.d(TAG, "Fetching teacher details for room ID: " + roomId);

        db.collection("rooms")
                .document(roomId)
                .get()
                .addOnSuccessListener(roomDoc -> {
                    if (roomDoc.exists()) {
                        // Fetch teacher details
                        String teacherId = roomDoc.getString("teacherId");
                        if (teacherId != null) {
                            fetchTeacherName(teacherId);
                        } else {
                            addTeacherNameToUI("Unknown Teacher");
                        }

                        // Fetch admin ID
                        String adminId = roomDoc.getString("adminId");
                        if (adminId != null) {
                            fetchAdminName(adminId);
                        } else {
                            addAdminNameToUI("Unknown Admin");
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

                        // Display in UI
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
                        String middleName = adminDoc.getString("middleName");
                        String lastName = adminDoc.getString("lastName");

                        String fullName = firstName + " " +
                                (middleName != null ? middleName + " " : "") + lastName;

                        Log.d(TAG, "Admin Name: " + fullName);
                        addAdminNameToUI(fullName);
                    } else {
                        Log.e(TAG, "Admin document not found for ID: " + adminId);
                        addAdminNameToUI("Unknown Admin");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching admin details for ID: " + adminId, e);
                    addAdminNameToUI("Error fetching admin details");
                });
    }

    private void addAdminNameToUI(String adminName) {
        if (adminNameTextView == null) {
            adminNameTextView = new TextView(this);
            containerLayout.addView(adminNameTextView, 1); // Add admin name below the teacher name
        }

        if (!"Unknown Admin".equals(adminName)) {
            adminNameTextView.setText("Admin: " + adminName);
            adminNameTextView.setPadding(8, 20, 8, 20);
            adminNameTextView.setGravity(Gravity.CENTER);
            adminNameTextView.setTextSize(18);
            adminNameTextView.setTypeface(null, android.graphics.Typeface.BOLD);
            adminNameTextView.setVisibility(View.VISIBLE);
        } else if (teacherNameTextView != null && teacherNameTextView.getVisibility() == View.VISIBLE) {
            // Hide "Unknown Admin" if a valid teacher name is displayed
            adminNameTextView.setVisibility(View.GONE);
        } else {
            adminNameTextView.setText("Admin: Unknown Admin");
            adminNameTextView.setVisibility(View.VISIBLE);
        }
    }



    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(timestamp.toDate());
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
        if (teacherNameTextView == null) {
            teacherNameTextView = new TextView(this);
            containerLayout.addView(teacherNameTextView, 0); // Add teacher name at the top
        }

        if (!"Unknown Teacher".equals(teacherName)) {
            teacherNameTextView.setText("Teacher: " + teacherName);
            teacherNameTextView.setPadding(8, 50, 8, 50);
            teacherNameTextView.setGravity(Gravity.CENTER);
            teacherNameTextView.setTextSize(18);
            teacherNameTextView.setTypeface(null, android.graphics.Typeface.BOLD);
            teacherNameTextView.setVisibility(View.VISIBLE);

            // Hide the "Unknown Admin" if a valid teacher name is displayed
            if (adminNameTextView != null && adminNameTextView.getText().toString().contains("Unknown Admin")) {
                adminNameTextView.setVisibility(View.GONE);
            }
        } else {
            teacherNameTextView.setVisibility(View.GONE);
        }
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

                        addStudentRowToTable(idNumber, fullName, studentId);
                    } else {
                        Log.e(TAG, "Student document not found for ID: " + studentId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching student details for ID: " + studentId, e);
                    Toast.makeText(this, "Error fetching student details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }


    private void addStudentRowToTable(String idNumber, String fullName, String studentId) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        // Add "No." column with the counter
        TextView noTextView = new TextView(this);
        noTextView.setText(String.valueOf(studentCounter++));
        noTextView.setPadding(8, 8, 8, 8);
        noTextView.setGravity(Gravity.CENTER);
        row.addView(noTextView);

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

        // Add long press listener to show delete confirmation
        row.setOnLongClickListener(view -> {
            showDeleteConfirmationDialog(studentId);
            return true;
        });

        tableLayout.addView(row);
    }

    private void showDeleteConfirmationDialog(String studentId) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Unenroll Student")
                .setMessage("Are you sure you want to un-enroll this student?")
                .setPositiveButton("Yes", (dialog, which) -> unenrollStudentFromRoom(studentId))
                .setNegativeButton("No", null)
                .show();
    }


    private void unenrollStudentFromRoom(String studentId) {
        String roomId = getIntent().getStringExtra("roomId");
        if (roomId == null || roomId.isEmpty()) {
            Toast.makeText(this, "Room ID is missing!", Toast.LENGTH_LONG).show();
            return;
        }

        String studentPath = "rooms/" + roomId + "/students/" + studentId;
        db.document(studentPath)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Student successfully unenrolled.", Toast.LENGTH_SHORT).show();
                    reloadStudentList(); // Refresh the list properly
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to unenroll student: " + studentPath, e);
                    Toast.makeText(this, "Failed to unenroll student.", Toast.LENGTH_SHORT).show();
                });
    }


    private void deleteStudentFromRoom(String studentId) {
        String roomId = getIntent().getStringExtra("roomId");
        if (roomId == null || roomId.isEmpty()) {
            Toast.makeText(this, "Room ID is missing!", Toast.LENGTH_LONG).show();
            return;
        }

        String studentPath = "rooms/" + roomId + "/students/" + studentId;
        db.document(studentPath)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Student un-enrolled successfully.", Toast.LENGTH_SHORT).show();
                    reloadStudentList();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete student from room: " + studentPath, e);
                    Toast.makeText(this, "Failed to un-enroll student.", Toast.LENGTH_SHORT).show();
                });
    }

    private void reloadStudentList() {
        tableLayout.removeAllViews(); // Clear all rows, including headers
        studentCounter = 1; // Reset student counter
        String roomId = getIntent().getStringExtra("roomId");
        if (roomId != null && !roomId.isEmpty()) {
            fetchStudentsFromRoom(roomId); // Fetch updated student list
        }
    }



    private void addEmptyRowToTable(String message) {
        tableLayout.removeAllViews(); // Clear any previous content including headers

        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        TextView messageTextView = new TextView(this);
        messageTextView.setText(message);
        messageTextView.setPadding(8, 8, 8, 8);
        messageTextView.setGravity(Gravity.CENTER);
        messageTextView.setTextSize(16);
        messageTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(messageTextView);

        tableLayout.addView(row);
    }


    private void addTableHeader() {
        TableRow headerRow = new TableRow(this);
        headerRow.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        // "No." Column Header
        TextView noHeader = new TextView(this);
        noHeader.setText("No.");
        noHeader.setPadding(8, 8, 8, 8);
        noHeader.setGravity(Gravity.CENTER);
        noHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        headerRow.addView(noHeader);

        // "ID Number" Column Header
        TextView idNumberHeader = new TextView(this);
        idNumberHeader.setText("ID Number");
        idNumberHeader.setPadding(8, 8, 8, 8);
        idNumberHeader.setGravity(Gravity.CENTER);
        idNumberHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        headerRow.addView(idNumberHeader);

        // "Name" Column Header
        TextView nameHeader = new TextView(this);
        nameHeader.setText("Name");
        nameHeader.setPadding(8, 8, 8, 8);
        nameHeader.setGravity(Gravity.CENTER);
        nameHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        headerRow.addView(nameHeader);

        tableLayout.addView(headerRow);
    }

}
