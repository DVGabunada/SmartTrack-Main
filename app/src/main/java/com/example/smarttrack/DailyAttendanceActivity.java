package com.example.smarttrack;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DailyAttendanceActivity extends AppCompatActivity {

    private static final String TAG = "DailyAttendance";

    private ListView dailyAttendanceList;
    private TextView emptyMessage;

    private FirebaseFirestore db;
    private ArrayList<String> attendanceRecords;
    private ArrayList<String> roomIds;
    private ArrayList<String> roomNames;
    private ArrayAdapter<String> listViewAdapter;

    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_attendance);

        // Retrieve UID from Intent first
        uid = getIntent().getStringExtra("uid");

        // Fallback to FirebaseAuth UID if not provided in the intent
        if (uid == null) {
            uid = FirebaseAuth.getInstance().getUid();
        }

        // If still null, log error and exit
        if (uid == null) {
            Log.e(TAG, "No UID available. Cannot proceed.");
            Toast.makeText(this, "Error: No user ID found. Please log in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        } else {
            Log.d(TAG, "UID successfully retrieved: " + uid);
        }

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize views
        dailyAttendanceList = findViewById(R.id.dailyAttendanceList);
        emptyMessage = findViewById(R.id.emptyMessage);

        // Initialize lists
        attendanceRecords = new ArrayList<>();
        roomIds = new ArrayList<>();
        roomNames = new ArrayList<>();

        // Set up the ListView adapter
        setupListViewAdapter();

        // Load rooms for the student
        loadStudentRooms();
    }

    private void setupListViewAdapter() {
        listViewAdapter = new ArrayAdapter<String>(
                this,
                R.layout.list_item_attendance,
                attendanceRecords
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.list_item_attendance, parent, false);
                }

                TextView textRoomName      = convertView.findViewById(R.id.textRoomName);
                TextView textTimeIn        = convertView.findViewById(R.id.textTimeIn);
                TextView textTimeOut       = convertView.findViewById(R.id.textTimeOut);
                TextView textStatusLabel   = convertView.findViewById(R.id.textStatusLabel);
                TextView textStatusValue   = convertView.findViewById(R.id.textStatusValue);

                String record = getItem(position);
                if (record != null) {
                    // Example of record:
                    // "Room Name: Zairen (Kamunggay) | Time In: 12:10 am, Time Out: 12:33 am, Status: Present"
                    String[] parts = record.split(" \\| ");
                    if (parts.length >= 2) {
                        textRoomName.setText(parts[0].replace("Room Name:", "").trim());

                        // timeIn/timeOut/status
                        String[] detailParts = parts[1].split(",");
                        if (detailParts.length >= 3) {
                            textTimeIn.setText(detailParts[0].trim());   // "Time In: 12:10 am"
                            textTimeOut.setText(detailParts[1].trim()); // "Time Out: 12:33 am"

                            // The 3rd part is something like "Status: Present"
                            String statusRaw = detailParts[2].trim();    // e.g. "Status: Present"

                            // If you only want "Present" or "Late" etc.
                            // you can remove "Status:" to just keep the value.
                            // e.g. "Present"
                            String statusValueOnly = statusRaw.replace("Status:", "").trim();
                            textStatusValue.setText(statusValueOnly);

                            // Make the label "Status" small black text:
                            textStatusLabel.setText("Status");
                            textStatusLabel.setTextColor(getResources().getColor(android.R.color.black));

                            // Then color the big status text:
                            if (statusValueOnly.equalsIgnoreCase("Present")) {
                                textStatusValue.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                            } else if (statusValueOnly.equalsIgnoreCase("Late")) {
                                textStatusValue.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                            } else {
                                textStatusValue.setTextColor(getResources().getColor(android.R.color.black));
                            }
                        }
                    }
                }

                return convertView;
            }



        };


        dailyAttendanceList.setAdapter(listViewAdapter);
    }

    private void loadStudentRooms() {
        // Query all rooms to find the ones where the student is enrolled
        db.collection("rooms")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    roomIds.clear();
                    roomNames.clear();

                    if (queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "No rooms found in the database.");
                        showNoRecordsMessage("No rooms available.");
                        return;
                    }

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String roomId = document.getId(); // Room ID
                        checkStudentEnrollment(roomId, document); // Check if the student is enrolled
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching rooms.", e);
                    showNoRecordsMessage("Error loading rooms.");
                });
    }


    private void checkStudentEnrollment(String roomId, QueryDocumentSnapshot roomDocument) {
        // Check if the student is enrolled in this room
        db.collection("rooms")
                .document(roomId)
                .collection("students")
                .document(uid)
                .get()
                .addOnSuccessListener(studentDoc -> {
                    if (studentDoc.exists()) {
                        Log.d(TAG, "Student is enrolled in room: " + roomId);
                        loadRoomDetails(roomId);
                        // Get room details
                        String subjectName = roomDocument.getString("subjectName");
                        String section = roomDocument.getString("section");

                        if (subjectName == null) subjectName = "Unknown Subject";
                        if (section == null) section = "Unknown Section";

                        String roomName = subjectName + " (" + section + ")";
                        roomIds.add(roomId);
                        roomNames.add(roomName);

                        // Fetch attendance for this room (only fetch for the first room as an example)
                        if (roomIds.size() == 1) {
                            fetchTodayAttendance(roomId, uid);
                        }

                        listViewAdapter.notifyDataSetChanged(); // Update UI
                    } else {
                        Log.d(TAG, "Student is not enrolled in room: " + roomId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking student enrollment in room: " + roomId, e);
                });
    }



    private void loadRoomDetails(String roomId) {
        db.collection("rooms")
                .document(roomId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String subjectName = documentSnapshot.getString("subjectName");
                        String section = documentSnapshot.getString("section");
                        if (subjectName == null) subjectName = "Unknown Subject";
                        if (section == null) section = "Unknown Section";

                        String roomName = subjectName + " (" + section + ")";
                        roomIds.add(roomId);
                        roomNames.add(roomName);

                        if (roomIds.size() == 1) {
                            fetchTodayAttendance(roomId, uid);
                        }

                    } else {
                        Log.d(TAG, "Room document " + roomId + " does not exist.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading room details", e);
                });
    }

    private void fetchTodayAttendance(String roomId, String studentUid) {
        Log.d(TAG, "Fetching attendance for roomId=" + roomId + ", uid=" + studentUid);

        // Get today's date in YYYYMMDD format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String todayDate = dateFormat.format(new Date());

        db.collection("rooms")
                .document(roomId)
                .collection("students")
                .document(studentUid)
                .collection("attendance")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    attendanceRecords.clear();
                    SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Timestamp date = document.getTimestamp("date");
                        Timestamp timeIn = document.getTimestamp("timeIn");
                        Timestamp timeOut = document.getTimestamp("timeOut");
                        String status = document.getString("status"); // Retrieve the status field

                        // Convert Firestore timestamp to YYYYMMDD format for comparison
                        if (date != null && dateFormat.format(date.toDate()).equals(todayDate)) {
                            Log.d(TAG, "✅ Found attendance for today in room: " + roomId);

                            String timeDetails = "Time In: " + (timeIn != null ? timeFormat.format(timeIn.toDate()) : "N/A");
                            timeDetails += ", Time Out: " + (timeOut != null ? timeFormat.format(timeOut.toDate()) : "N/A");
                            timeDetails += ", Status: " + (status != null ? status : "N/A"); // Add the status to the details

                            String record = "Room Name: " + roomNames.get(roomIds.indexOf(roomId)) + " | " + timeDetails;
                            attendanceRecords.add(record);
                            Log.d(TAG, "Attendance record added: " + record);
                        }
                    }

                    listViewAdapter.notifyDataSetChanged();

                    if (attendanceRecords.isEmpty()) {
                        showNoRecordsMessage("No attendance records for today.");
                    } else {
                        showListView();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error fetching attendance", e);
                    showNoRecordsMessage("Error fetching attendance.");
                });
    }



    private void showNoRecordsMessage(String message) {
        dailyAttendanceList.setVisibility(View.GONE);
        emptyMessage.setVisibility(View.VISIBLE);
        emptyMessage.setText(message);
    }

    private void showListView() {
        emptyMessage.setVisibility(View.GONE);
        dailyAttendanceList.setVisibility(View.VISIBLE);
    }
}