package com.example.smarttrack;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;  // For feedback dialog
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Displays "Reports" for a student's attendance in a date range.
 * Updated to show:
 *   - Date
 *   - Time In, In-Location, Time In Status
 *   - Time Out, Out-Location, Time Out Status
 */
public class Students_Report extends AppCompatActivity {

    // UI Elements (bottom navigation)
    private ImageView roomIcon;
    private ImageView homeIcon;
    private ImageView scheduleIcon;

    // Date filter views
    private TextView tvStartDate;
    private TextView tvEndDate;
    private Button btnFilter;

    // Range data
    private String selectedStartDate = null; // e.g., "2023-01-01"
    private String selectedEndDate   = null; // e.g., "2023-01-31"

    // Drawer layout and navigation
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView navUsername, navIdNumber;

    // RecyclerView for attendance
    private RecyclerView attendanceRecyclerView;
    private AttendanceAdapter attendanceAdapter;
    private List<AttendanceRecord> attendanceRecords; // Data model list

    // Student UID
    private String uid;

    // ProgressDialog for loading
    private ProgressDialog progressDialog;
    private int pendingQueriesCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_report);

        // Retrieve UID from the Intent or FirebaseAuth
        uid = getIntent().getStringExtra("uid");
        if (uid == null) {
            uid = FirebaseAuth.getInstance().getUid();
        }

        // Setup the custom toolbar
        setupToolbar();

        // Initialize all views and setup navigation
        initializeViews();
        setupNavigationDrawer();
        setupClickListeners();

        // Initialize RecyclerView and its adapter
        attendanceRecords = new ArrayList<>();
        attendanceAdapter = new AttendanceAdapter(attendanceRecords);
        attendanceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        attendanceRecyclerView.setAdapter(attendanceAdapter);

        // Fetch student details for the navigation drawer
        fetchStudentDetails(uid);

        TextView manualLink = findViewById(R.id.manualLink);

        manualLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://www.figma.com/proto/Jm2THxINfHdGGAZCXLuMUr/REPORTTTTTTTTTTTTTTTTTS?node-id=17-85&t=2dn3wcG3IWe2tG4S-1&scaling=min-zoom&content-scaling=fixed&page-id=17%3A6";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        });


    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Hide default title to use our custom TextView
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("Reports");
    }

    private void initializeViews() {
        // Bottom navigation icons
        roomIcon = findViewById(R.id.roomIcon);
        homeIcon = findViewById(R.id.homeIcon);
        scheduleIcon = findViewById(R.id.scheduleIcon);

        // Date filter views
        tvStartDate = findViewById(R.id.tvStartDate);
        tvEndDate   = findViewById(R.id.tvEndDate);
        btnFilter   = findViewById(R.id.btnFilter);

        // Drawer components
        drawerLayout   = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navUsername    = navigationView.findViewById(R.id.navUsername);
        navIdNumber    = navigationView.findViewById(R.id.navIdNumber);

        // RecyclerView
        attendanceRecyclerView = findViewById(R.id.attendanceRecyclerView);

        // Initialize the ProgressDialog
        progressDialog = new ProgressDialog(Students_Report.this);
        progressDialog.setMessage("Loading...");
        progressDialog.setCancelable(false);
    }

    private void setupClickListeners() {
        // Show date picker for start date
        tvStartDate.setOnClickListener(v -> showDatePickerDialog(true));

        // Show date picker for end date
        tvEndDate.setOnClickListener(v -> showDatePickerDialog(false));

        // Filter button
        btnFilter.setOnClickListener(v -> {
            if (selectedStartDate == null || selectedEndDate == null) {
                Toast.makeText(this, "Please select both start and end date.", Toast.LENGTH_SHORT).show();
            } else {
                // Execute method to query attendance within range
                fetchAttendanceInRange(selectedStartDate, selectedEndDate);
            }
        });

        // Bottom navigation icon clicks
        roomIcon.setOnClickListener(v -> navigateToRoom());
        homeIcon.setOnClickListener(v -> navigateToHome());
        scheduleIcon.setOnClickListener(v -> navigateToCalendar());
    }

    private void setupNavigationDrawer() {
        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> handleLogout());
    }

    private void handleLogout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(Students_Report.this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Show a date picker to set either start or end date.
     */
    private void showDatePickerDialog(boolean isStart) {
        final DatePickerDialog.OnDateSetListener listener = (DatePicker view, int year, int month, int day) -> {
            // Format date as YYYY-MM-DD
            String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, (month + 1), day);

            if (isStart) {
                selectedStartDate = dateStr;
                tvStartDate.setText(dateStr);
            } else {
                selectedEndDate = dateStr;
                tvEndDate.setText(dateStr);
            }
        };

        // Show the dialog using today's date as default
        final Calendar c = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                listener,
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    // ---------- Navigation to other student pages ----------
    private void navigateToRoom() {
        Intent intent = new Intent(Students_Report.this, Students_Room.class);
        intent.putExtra("uid", uid);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void navigateToHome() {
        Intent intent = new Intent(Students_Report.this, Students_Home.class);
        intent.putExtra("uid", uid);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void navigateToCalendar() {
        Intent intent = new Intent(Students_Report.this, Students_Calendar.class);
        intent.putExtra("uid", uid);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    // --------------------- KEY LOGIC: Fetch Attendance with ProgressDialog ---------------------

    /**
     * Show progress dialog, then fetch attendance from both Rooms & Events within the given date range.
     */
    private void fetchAttendanceInRange(String startDateStr, String endDateStr) {
        // Clear old data
        attendanceRecords.clear();
        attendanceAdapter.notifyDataSetChanged();

        // Reset the counter and show the dialog
        pendingQueriesCount = 0;
        showLoading();

        // Convert the date strings to Date objects
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date startDate = sdf.parse(startDateStr);
            Date endDate   = sdf.parse(endDateStr);

            if (startDate == null || endDate == null) {
                Toast.makeText(this, "Invalid date range. Please try again.", Toast.LENGTH_SHORT).show();
                hideLoading();
                return;
            }

            // Convert to Firestore Timestamps
            Timestamp startTimestamp = new Timestamp(startDate);
            // Move endTimestamp to the end of that day
            Calendar endCal = Calendar.getInstance();
            endCal.setTime(endDate);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);
            Timestamp endTimestamp = new Timestamp(endCal.getTime());

            // Start fetching attendance data
            queryAllRoomsForRangeDailyCodes(uid, startTimestamp, endTimestamp);
            queryAllEventsForRangeDailyCodes(uid, startTimestamp, endTimestamp);

        } catch (ParseException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to parse date.", Toast.LENGTH_SHORT).show();
            hideLoading();
        }
    }

    /**
     * Shows the ProgressDialog if it's not already visible.
     */
    private void showLoading() {
        if (progressDialog != null && !progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    /**
     * Hides the ProgressDialog if it's visible.
     */
    private void hideLoading() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    // ----- Utility methods to track Firestore async calls -----

    private void incrementPendingQueries() {
        if (pendingQueriesCount == 0) {
            showLoading();
        }
        pendingQueriesCount++;
    }

    private void decrementPendingQueries() {
        pendingQueriesCount--;
        if (pendingQueriesCount <= 0) {
            hideLoading();
        }
    }

    // =========== QUERY ROOM ATTENDANCE (USING dailyCodes) ===========

    private void queryAllRoomsForRangeDailyCodes(String uid, Timestamp startTs, Timestamp endTs) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Track processed room IDs to avoid duplicates
        Set<String> processedRoomIds = new HashSet<>();

        incrementPendingQueries();
        db.collection("rooms")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    decrementPendingQueries();

                    if (querySnapshot.isEmpty()) {
                        return;
                    }
                    // For each room, check if user is enrolled
                    for (QueryDocumentSnapshot roomDoc : querySnapshot) {
                        String roomId = roomDoc.getId();
                        if (!processedRoomIds.contains(roomId)) {
                            processedRoomIds.add(roomId);
                            checkStudentEnrollmentDailyCodes(roomId, uid, startTs, endTs, roomDoc);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    decrementPendingQueries();
                    Toast.makeText(this, "Error fetching rooms: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void checkStudentEnrollmentDailyCodes(String roomId,
                                                  String uid,
                                                  Timestamp startTs,
                                                  Timestamp endTs,
                                                  DocumentSnapshot roomDoc) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        incrementPendingQueries();
        db.collection("rooms")
                .document(roomId)
                .collection("students")
                .document(uid)
                .get()
                .addOnSuccessListener(studentDoc -> {
                    decrementPendingQueries();
                    if (studentDoc.exists()) {
                        // Student is enrolled in this room, so fetch dailyCodes in range
                        fetchDailyCodesForRoomInRange(roomId, uid, startTs, endTs, roomDoc);
                    }
                })
                .addOnFailureListener(e -> {
                    decrementPendingQueries();
                    Log.e("Students_Report", "Error checking enrollment in room " + roomId, e);
                });
    }

    private void fetchDailyCodesForRoomInRange(String roomId,
                                               String uid,
                                               Timestamp startTs,
                                               Timestamp endTs,
                                               DocumentSnapshot roomDoc) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        incrementPendingQueries();
        db.collection("rooms")
                .document(roomId)
                .collection("dailyCodes")
                .whereGreaterThanOrEqualTo("generatedDate", startTs)
                .whereLessThanOrEqualTo("generatedDate", endTs)
                .get()
                .addOnSuccessListener(dailyCodesSnap -> {
                    decrementPendingQueries();
                    if (!dailyCodesSnap.isEmpty()) {
                        for (DocumentSnapshot dailyCodeDoc : dailyCodesSnap.getDocuments()) {
                            String dailyCodeId = dailyCodeDoc.getId();
                            fetchStudentAttendanceDoc(roomId, uid, dailyCodeId, roomDoc);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    decrementPendingQueries();
                    Log.e("Students_Report", "Error fetching daily codes in range", e);
                });
    }

    private void fetchStudentAttendanceDoc(String roomId,
                                           String uid,
                                           String dailyCodeId,
                                           DocumentSnapshot roomDoc) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Extract room info for display
        String subjectName = roomDoc.getString("subjectName");
        String section     = roomDoc.getString("section");
        if (subjectName == null) subjectName = "Unknown Subject";
        if (section == null)     section = "Unknown Section";
        String roomName = subjectName + " (" + section + ")";

        incrementPendingQueries();
        db.collection("rooms")
                .document(roomId)
                .collection("students")
                .document(uid)
                .collection("attendance")
                .document(dailyCodeId)
                .get()
                .addOnSuccessListener(docSnap -> {
                    decrementPendingQueries();
                    if (docSnap.exists()) {
                        // Read fields from the doc:
                        Timestamp timeIn           = docSnap.getTimestamp("timeIn");
                        Timestamp timeOut          = docSnap.getTimestamp("timeOut");
                        String timeInStatus        = docSnap.getString("statusTimeIn");
                        String timeOutStatus       = docSnap.getString("statusTimeOut");
                        String timeInLocation      = docSnap.getString("locationIn");
                        String timeOutLocation     = docSnap.getString("locationOut");
                        String feedback            = docSnap.getString("feedback");

                        // Build an AttendanceRecord
                        AttendanceRecord record = new AttendanceRecord();
                        record.roomName         = roomName;
                        record.timeInLocation   = (timeInLocation  != null) ? timeInLocation  : "";
                        record.timeOutLocation  = (timeOutLocation != null) ? timeOutLocation : "";
                        record.timeInStatus     = (timeInStatus    != null) ? timeInStatus    : "N/A";
                        record.timeOutStatus    = (timeOutStatus   != null) ? timeOutStatus   : "N/A";
                        record.feedback         = (feedback        != null) ? feedback        : "";

                        // For Date, let's set from timeIn if available; otherwise parse dailyCode
                        if (timeIn != null) {
                            record.date   = timeIn.toDate();
                        } else {
                            record.date   = parseDailyCodeDate(dailyCodeId);
                        }
                        record.timeIn  = (timeIn  != null) ? timeIn.toDate()  : null;
                        record.timeOut = (timeOut != null) ? timeOut.toDate() : null;

                        // If you still want to compute an "early out" logic, etc., do so here.
                        // But now we keep timeOutStatus from the doc directly, or whatever logic you prefer.

                        attendanceRecords.add(record);
                        attendanceAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    decrementPendingQueries();
                    Log.e("Students_Report", "Error fetching daily code doc: " + dailyCodeId, e);
                });
    }

    private Date parseDailyCodeDate(String dailyCodeId) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            return sdf.parse(dailyCodeId);
        } catch (Exception e) {
            return null;
        }
    }

    // =========== QUERY EVENT ATTENDANCE (SIMILAR dailyCodes LOGIC) ===========

    private void queryAllEventsForRangeDailyCodes(String uid, Timestamp startTs, Timestamp endTs) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        incrementPendingQueries();
        db.collection("events")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    decrementPendingQueries();

                    if (querySnapshot.isEmpty()) {
                        Log.d("Events_Debug", "No events found.");
                        return;
                    }
                    // For each event, look at its "rooms" subcollection
                    for (QueryDocumentSnapshot eventDoc : querySnapshot) {
                        final String eventId = eventDoc.getId();

                        String et = eventDoc.getString("title");
                        if (et == null) et = "Untitled Event";
                        final String eventTitle = et;

                        incrementPendingQueries();
                        eventDoc.getReference()
                                .collection("rooms")
                                .get()
                                .addOnSuccessListener(roomSnap -> {
                                    decrementPendingQueries();
                                    for (QueryDocumentSnapshot roomDoc : roomSnap) {
                                        String roomId = roomDoc.getId();
                                        checkStudentEnrollmentForEventDailyCodes(eventId, eventTitle,
                                                roomId, uid, startTs, endTs);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    decrementPendingQueries();
                                    Log.e("Events_Debug", "Failed to get rooms in event " + eventId, e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    decrementPendingQueries();
                    Log.e("Events_Debug", "Error fetching events", e);
                });
    }

    private void checkStudentEnrollmentForEventDailyCodes(String eventId,
                                                          String eventTitle,
                                                          String roomId,
                                                          String uid,
                                                          Timestamp startTs,
                                                          Timestamp endTs) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        incrementPendingQueries();
        db.collection("events")
                .document(eventId)
                .collection("rooms")
                .document(roomId)
                .collection("students")
                .document(uid)
                .get()
                .addOnSuccessListener(studentDoc -> {
                    decrementPendingQueries();
                    if (studentDoc.exists()) {
                        // Now fetch dailyCodes in the event if you store them similarly
                        fetchEventDailyCodesInRange(eventId, eventTitle, roomId, uid, startTs, endTs);
                    }
                })
                .addOnFailureListener(e -> {
                    decrementPendingQueries();
                    Log.e("Events_Debug", "Error checking event enrollment: " + e.getMessage(), e);
                });
    }

    private void fetchEventDailyCodesInRange(String eventId,
                                             String eventTitle,
                                             String roomId,
                                             String uid,
                                             Timestamp startTs,
                                             Timestamp endTs) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        incrementPendingQueries();
        db.collection("events")
                .document(eventId)
                .collection("rooms")
                .document(roomId)
                .collection("dailyCodes")
                .whereGreaterThanOrEqualTo("generatedDate", startTs)
                .whereLessThanOrEqualTo("generatedDate", endTs)
                .get()
                .addOnSuccessListener(dailyCodesSnap -> {
                    decrementPendingQueries();
                    if (!dailyCodesSnap.isEmpty()) {
                        for (DocumentSnapshot dailyCodeDoc : dailyCodesSnap.getDocuments()) {
                            String dailyCodeId = dailyCodeDoc.getId();
                            fetchEventStudentAttendanceDoc(eventId, eventTitle, roomId, uid, dailyCodeId);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    decrementPendingQueries();
                    Log.e("Events_Debug", "Error fetching event dailyCodes in range", e);
                });
    }

    private void fetchEventStudentAttendanceDoc(String eventId,
                                                String eventTitle,
                                                String roomId,
                                                String uid,
                                                String dailyCodeId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        incrementPendingQueries();
        db.collection("events")
                .document(eventId)
                .collection("rooms")
                .document(roomId)
                .collection("students")
                .document(uid)
                .collection("attendance")
                .document(dailyCodeId)
                .get()
                .addOnSuccessListener(docSnap -> {
                    decrementPendingQueries();
                    if (docSnap.exists()) {
                        // Parse fields
                        Timestamp timeIn           = docSnap.getTimestamp("timeIn");
                        Timestamp timeOut          = docSnap.getTimestamp("timeOut");
                        String timeInStatus        = docSnap.getString("statusTimeIn");
                        String timeOutStatus       = docSnap.getString("statusTimeOut");
                        String timeInLocation      = docSnap.getString("locationIn");
                        String timeOutLocation     = docSnap.getString("locationOut");
                        String feedback            = docSnap.getString("feedback");

                        AttendanceRecord record = new AttendanceRecord();
                        record.roomName         = eventTitle + " (Event)";
                        record.timeInLocation   = (timeInLocation  != null) ? timeInLocation  : "";
                        record.timeOutLocation  = (timeOutLocation != null) ? timeOutLocation : "";
                        record.timeInStatus     = (timeInStatus    != null) ? timeInStatus    : "N/A";
                        record.timeOutStatus    = (timeOutStatus   != null) ? timeOutStatus   : "N/A";
                        record.feedback         = (feedback        != null) ? feedback        : "";

                        if (timeIn != null) {
                            record.date = timeIn.toDate();
                        } else {
                            record.date = parseDailyCodeDate(dailyCodeId);
                        }
                        record.timeIn  = (timeIn  != null) ? timeIn.toDate()  : null;
                        record.timeOut = (timeOut != null) ? timeOut.toDate() : null;

                        attendanceRecords.add(record);
                        attendanceAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    decrementPendingQueries();
                    Log.e("Events_Debug", "Error fetching event dailyCode doc: " + dailyCodeId, e);
                });
    }

    // ---------- Fetch and display user's name/ID in nav drawer ----------
    private void fetchStudentDetails(String uid) {
        if (uid == null) {
            navUsername.setText("Unknown User");
            navIdNumber.setText("");
            return;
        }

        FirebaseFirestore.getInstance().collection("students")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName  = document.getString("lastName");
                        String idNumber  = document.getString("idNumber");

                        navUsername.setText(
                                (firstName != null ? firstName : "") + " " +
                                        (lastName  != null ? lastName  : "")
                        );
                        navIdNumber.setText(idNumber != null ? idNumber : "");
                    }
                })
                .addOnFailureListener(e -> {
                    navUsername.setText("Error fetching details");
                    navIdNumber.setText("");
                    Log.e("Students_Report", "Error fetching student details", e);
                });
    }

    // --------------------- RecyclerView ADAPTER & MODEL ---------------------

    /**
     * Model class to hold attendance data.
     */
    private static class AttendanceRecord {
        String roomName;
        Date   date;
        Date   timeIn;
        Date   timeOut;

        // Renamed to store separate statuses/locations
        String timeInStatus;
        String timeOutStatus;
        String timeInLocation;
        String timeOutLocation;

        // Additional optional feedback
        String feedback;
    }

    /**
     * RecyclerView Adapter for attendance records, inflating the CardView layout.
     * Now uses new fields: timeInLocation, timeInStatus, timeOutLocation, timeOutStatus.
     */
    private static class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {

        private final List<AttendanceRecord> data;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        public AttendanceAdapter(List<AttendanceRecord> data) {
            this.data = data;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // Inflate the updated CardView layout (item_attendance_record.xml)
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_attendance_record, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            AttendanceRecord record = data.get(position);

            String dateStr    = (record.date    != null) ? dateFormat.format(record.date)    : "N/A";
            String timeInStr  = (record.timeIn  != null) ? timeFormat.format(record.timeIn)  : "N/A";
            String timeOutStr = (record.timeOut != null) ? timeFormat.format(record.timeOut) : "N/A";

            // Set main info
            holder.tvRoomName.setText(record.roomName);
            holder.tvDate.setText("Date: " + dateStr);

            // Time In
            holder.tvTimeIn.setText("Time In: " + timeInStr);
            holder.tvInLocation.setText("Location: " + (record.timeInLocation.isEmpty() ? "N/A" : record.timeInLocation));
            holder.tvTimeInStatus.setText("Time In Status: " + record.timeInStatus);

            // Time Out
            holder.tvTimeOut.setText("Time Out: " + timeOutStr);
            holder.tvOutLocation.setText("Location: " + (record.timeOutLocation.isEmpty() ? "N/A" : record.timeOutLocation));
            holder.tvTimeOutStatus.setText("Time Out Status: " + record.timeOutStatus);

            // When a card is tapped, show the feedback dialog
            holder.itemView.setOnClickListener(v -> {
                String feedbackMessage = (record.feedback == null || record.feedback.trim().isEmpty())
                        ? "No feedback"
                        : record.feedback;
                new AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle("Feedback")
                        .setMessage(feedbackMessage)
                        .setPositiveButton("Close", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            // Existing
            TextView tvRoomName, tvDate;

            // Time In
            TextView tvTimeIn, tvInLocation, tvTimeInStatus;

            // Time Out
            TextView tvTimeOut, tvOutLocation, tvTimeOutStatus;

            ViewHolder(View itemView) {
                super(itemView);
                tvRoomName       = itemView.findViewById(R.id.tvRoomName);
                tvDate           = itemView.findViewById(R.id.tvDate);

                tvTimeIn         = itemView.findViewById(R.id.tvTimeIn);
                tvInLocation     = itemView.findViewById(R.id.tvInLocation);
                tvTimeInStatus   = itemView.findViewById(R.id.tvTimeInStatus);

                tvTimeOut        = itemView.findViewById(R.id.tvTimeOut);
                tvOutLocation    = itemView.findViewById(R.id.tvOutLocation);
                tvTimeOutStatus  = itemView.findViewById(R.id.tvTimeOutStatus);
            }
        }
    }
}
