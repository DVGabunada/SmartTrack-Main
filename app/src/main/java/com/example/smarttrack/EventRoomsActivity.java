package com.example.smarttrack;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class EventRoomsActivity extends AppCompatActivity {

    private static final String TAG = "EventRoomsActivity";
    private FirebaseFirestore db;

    private String eventId;
    private String eventTitle, eventDate, startTime, endTime, location;

    private TextView eventTitleText, eventDateText, eventTimeText, eventLocationText;
    private LinearLayout roomListContainer;
    private ProgressBar loadingIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_rooms);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Get data from Intent
        Intent intent = getIntent();
        eventId = intent.getStringExtra("eventId");
        eventTitle = intent.getStringExtra("eventTitle");
        eventDate = intent.getStringExtra("eventDate");
        startTime = intent.getStringExtra("startTime");
        endTime = intent.getStringExtra("endTime");
        location = intent.getStringExtra("location");

        // Initialize UI elements
        eventTitleText = findViewById(R.id.eventTitle);
        eventDateText = findViewById(R.id.eventDate);
        eventTimeText = findViewById(R.id.eventTime);
        eventLocationText = findViewById(R.id.eventLocation);
        roomListContainer = findViewById(R.id.roomListContainer);
        loadingIndicator = findViewById(R.id.loadingIndicator);

        // Set event details
        eventTitleText.setText(eventTitle);
        eventDateText.setText("Date: " + eventDate);
        eventTimeText.setText("Time: " + startTime + " - " + endTime);
        eventLocationText.setText("Location: " + location);

        // Fetch and display rooms for this event
        fetchEventRooms();
    }

    private void fetchEventRooms() {
        loadingIndicator.setVisibility(View.VISIBLE);
        roomListContainer.removeAllViews();

        db.collection("events")
                .document(eventId)
                .collection("rooms") // Fetch mapped rooms
                .get()
                .addOnSuccessListener(roomSnapshots -> {
                    loadingIndicator.setVisibility(View.GONE);

                    if (roomSnapshots.isEmpty()) {
                        Log.d(TAG, "⚠️ No rooms linked to this event.");
                        TextView noRoomsText = new TextView(this);
                        noRoomsText.setText("No rooms linked to this event.");
                        roomListContainer.addView(noRoomsText);
                        return;
                    }

                    for (QueryDocumentSnapshot roomDoc : roomSnapshots) {
                        String roomId = roomDoc.getId();
                        fetchRoomDetails(roomId);
                    }
                })
                .addOnFailureListener(e -> {
                    loadingIndicator.setVisibility(View.GONE);
                    Log.e(TAG, "❌ Error fetching rooms for event", e);
                });
    }

    private void fetchRoomDetails(String roomId) {
        db.collection("rooms")
                .document(roomId)
                .get()
                .addOnSuccessListener(roomDoc -> {
                    if (!roomDoc.exists()) {
                        Log.d(TAG, "⚠️ Room " + roomId + " does not exist in main collection.");
                        return;
                    }

                    String subjectName = roomDoc.getString("subjectName");
                    String section = roomDoc.getString("section");

                    if (subjectName == null) subjectName = "Unknown Subject";
                    if (section == null) section = "Unknown Section";

                    String buttonText = subjectName + " (" + section + ")";

                    Button roomButton = new Button(this);
                    roomButton.setText(buttonText);
                    roomButton.setPadding(16, 16, 16, 16);
                    roomButton.setBackgroundResource(R.drawable.button_gold_border);
                    roomButton.setTextColor(Color.WHITE);

                    // Show attendance popup for this room
                    roomButton.setOnClickListener(v -> showAttendancePopup(roomId, buttonText));

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, 8, 0, 8);
                    roomButton.setLayoutParams(params);

                    roomListContainer.addView(roomButton);
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Error fetching room details", e));
    }

    private void showAttendancePopup(String roomId, String roomName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_attendance_popup, null); // ✅ Load correct XML layout
        builder.setView(dialogView);

        TextView popupTitle = dialogView.findViewById(R.id.popupTitle);
        LinearLayout studentListContainer = dialogView.findViewById(R.id.studentListContainer);
        ProgressBar popupLoadingIndicator = dialogView.findViewById(R.id.popupLoadingIndicator);
        Button closeButton = dialogView.findViewById(R.id.closePopupButton);

        popupTitle.setText("Attendance - " + roomName);
        AlertDialog dialog = builder.create();
        dialog.show();

        closeButton.setOnClickListener(v -> dialog.dismiss());

        fetchAttendanceForRoom(roomId, studentListContainer, popupLoadingIndicator);
    }

    private void fetchAttendanceForRoom(String roomId, LinearLayout studentListContainer, ProgressBar popupLoadingIndicator) {
        popupLoadingIndicator.setVisibility(View.VISIBLE);
        studentListContainer.removeAllViews();

        db.collection("rooms")
                .document(roomId)
                .collection("students")
                .get()
                .addOnSuccessListener(studentSnapshots -> {
                    popupLoadingIndicator.setVisibility(View.GONE);

                    if (studentSnapshots.isEmpty()) {
                        Log.d(TAG, "⚠️ No students found in this room.");
                        TextView noDataText = new TextView(this);
                        noDataText.setText("No students attended today.");
                        studentListContainer.addView(noDataText);
                        return;
                    }

                    for (QueryDocumentSnapshot studentDoc : studentSnapshots) {
                        String studentId = studentDoc.getId();
                        fetchStudentAttendance(eventId, roomId, studentId, studentListContainer);

                    }
                })
                .addOnFailureListener(e -> {
                    popupLoadingIndicator.setVisibility(View.GONE);
                    Log.e(TAG, "❌ Error fetching students", e);
                });
    }

    // ✅ Get current day's timestamp (sets time to 00:00:00)
    private Timestamp getTodayTimestamp() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return new Timestamp(calendar.getTime());
    }

    // ✅ Check if two timestamps belong to the same day
    private boolean isSameDay(Timestamp ts1, Timestamp ts2) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return dateFormat.format(ts1.toDate()).equals(dateFormat.format(ts2.toDate()));
    }


    private void fetchStudentAttendance(String eventId, String roomId, String studentId, LinearLayout studentListContainer) {
        Log.d(TAG, "📌 Fetching attendance for student: " + studentId + " in event: " + eventId + " room: " + roomId);

        db.collection("events") // ✅ Fix: Start from events collection
                .document(eventId)
                .collection("rooms")
                .document(roomId)
                .collection("students")
                .document(studentId)
                .collection("attendance")
                .get()
                .addOnSuccessListener(attendanceSnapshots -> {
                    Log.d(TAG, "✅ Attendance query success. Documents found: " + attendanceSnapshots.size());

                    if (attendanceSnapshots.isEmpty()) {
                        Log.d(TAG, "⚠️ No attendance records found.");
                        fetchStudentName(studentId, "No Attendance", "N/A", "N/A", studentListContainer);
                        return;
                    }

                    // ✅ Get the first attendance document
                    DocumentSnapshot attendanceDoc = attendanceSnapshots.getDocuments().get(0);
                    String attendanceId = attendanceDoc.getId();
                    Log.d(TAG, "✅ Found attendance document ID: " + attendanceId);

                    fetchAttendanceDetails(eventId, roomId, studentId, attendanceId, studentListContainer);
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Error fetching attendance for student " + studentId, e));
    }



    private void fetchAttendanceDetails(String eventId, String roomId, String studentId, String attendanceId, LinearLayout studentListContainer) {
        Log.d(TAG, "📌 Fetching attendance details from event: " + eventId + " | room: " + roomId + " | student: " + studentId);

        db.collection("events")  // ✅ Start from events
                .document(eventId)
                .collection("rooms")
                .document(roomId)
                .collection("students")
                .document(studentId)
                .collection("attendance")
                .document(attendanceId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        Log.d(TAG, "⚠️ Attendance document does NOT exist.");
                        fetchStudentName(studentId, "No Attendance", "N/A", "N/A", studentListContainer);
                        return;
                    }

                    Log.d(TAG, "✅ Attendance document found: " + document.getId());

                    // ✅ Extract attendance details
                    String status = document.getString("status");
                    Timestamp timeInTimestamp = document.getTimestamp("timeIn");
                    String eventLocation = document.getString("eventLocation");

                    Log.d(TAG, "✅ Status: " + status);
                    Log.d(TAG, "✅ Time In: " + timeInTimestamp);
                    Log.d(TAG, "✅ Event Location: " + eventLocation);

                    String timeIn = (timeInTimestamp != null) ? formatTimestamp(timeInTimestamp) : "N/A";

                    if (status == null) status = "Unknown";

                    fetchStudentName(studentId, status, timeIn, eventLocation, studentListContainer);
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Error fetching attendance details for student " + studentId, e));
    }






    private String formatTimestamp(Timestamp timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(timestamp.toDate());
    }

    private void fetchStudentName(String studentId, String status, String timeIn, String eventLocation, LinearLayout studentListContainer) {
        db.collection("students")
                .document(studentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String firstName = documentSnapshot.getString("firstName");
                    String lastName = documentSnapshot.getString("lastName");

                    if (firstName == null) firstName = "Unknown";
                    if (lastName == null) lastName = "Student";

                    String studentName = firstName + " " + lastName;

                    // ✅ Ensure the attendance is properly displayed
                    TextView studentAttendanceText = new TextView(this);
                    studentAttendanceText.setText(studentName + " - " + status + " | Time In: " + timeIn + " | Location: " + eventLocation);
                    studentListContainer.addView(studentAttendanceText);
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Error fetching student name for " + studentId, e));
    }

}
