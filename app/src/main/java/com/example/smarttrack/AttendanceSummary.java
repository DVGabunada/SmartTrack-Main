package com.example.smarttrack;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AttendanceSummary that supports only "Class" or "Event" filtering (no "All").
 */
public class AttendanceSummary extends AppCompatActivity {

    private static final String TAG = "AttendanceSummary";

    // UI references
    private LinearLayout studentsContainer;
    private TextView tvNoOfStudents;
    private Button btnExportCsv;
    private SearchView searchView;
    private ImageView sortButton;
    private Spinner attendanceFilterSpinner;

    // Firestore
    private FirebaseFirestore db;

    // Data from Intent
    private String roomId;
    private String startDateStr;
    private String endDateStr;

    // Range timestamps
    private Timestamp startTimestamp;
    private Timestamp endTimestamp;

    // Room doc fields
    private String teacherId   = "";
    private String teacherName = "";
    private String subjectCode = "";
    private String subjectName = "";
    private String section     = "";

    // If you need daily endTime
    private Timestamp roomEndTime = null;

    // If you track official class start/end
    private Timestamp classStartDate = null;
    private Timestamp classEndDate   = null;

    // Room schedule (if used)
    private List<String> roomSchedule = new ArrayList<>();

    // Daily code doc IDs (e.g. "20250330")
    private final List<String> dailyCodeIds = new ArrayList<>();

    // For CSV export (location flags will NOT be included in CSV)
    private final List<AttendanceRecord> attendanceData = new ArrayList<>();

    // Maps userId => all attendance records
    private final Map<String, StudentAttendance> studentAttendanceMap = new HashMap<>();

    // Set of enrolled userIds (from /rooms/{roomId}/students)
    private final Set<String> enrolledUserIds = new HashSet<>();

    // For searching
    private String currentSearchQuery = "";

    // Sorting
    private boolean isSortAscending = true;

    // Current filter is either "Class" or "Event"
    private String currentAttendanceFilter = "Class"; // default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_summary);

        // UI references
        studentsContainer = findViewById(R.id.studentsContainer);
        tvNoOfStudents    = findViewById(R.id.tvNoOfStudents);
        btnExportCsv      = findViewById(R.id.btnExportCsv);
        searchView        = findViewById(R.id.searchView);
        sortButton        = findViewById(R.id.sortButton);
        attendanceFilterSpinner = findViewById(R.id.attendanceFilterSpinner);

        // Firestore
        db = FirebaseFirestore.getInstance();

        // Get extras
        roomId       = getIntent().getStringExtra("roomId");
        startDateStr = getIntent().getStringExtra("startDate");
        endDateStr   = getIntent().getStringExtra("endDate");

        Log.d(TAG, "Room: " + roomId + ", range = " + startDateStr + " to " + endDateStr);

        // Parse date range
        if (!parseDateRange()) {
            finish();
            return;
        }

        // Check storage permission
        checkStoragePermission();

        // Set up search
        searchView.setQueryHint("Search student...");
        searchView.setIconifiedByDefault(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentSearchQuery = query;
                refreshUI();
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText;
                refreshUI();
                return true;
            }
        });

        // Sort button
        sortButton.setOnClickListener(v -> {
            isSortAscending = !isSortAscending;
            refreshUI();
        });

        // Spinner for "Class" / "Event" only
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.attendance_filter_array, android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        attendanceFilterSpinner.setAdapter(adapter);
        attendanceFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentAttendanceFilter = parent.getItemAtPosition(position).toString();
                // We only have "Class" or "Event" in arrays.xml
                refreshUI();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Start chain: fetch room -> teacher -> daily codes -> students
        fetchRoomDataAndThenLoadTeacher();

        // Export button
        btnExportCsv.setOnClickListener(view -> exportToCsv());
    }

    private boolean parseDateRange() {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-M-d", Locale.getDefault());
        try {
            // Start
            Date sDate = sdf.parse(startDateStr);
            Calendar startCal = Calendar.getInstance();
            if (sDate != null) {
                startCal.setTime(sDate);
            }
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);
            startTimestamp = new Timestamp(startCal.getTime());

            // End
            Date eDate = sdf.parse(endDateStr);
            Calendar endCal = Calendar.getInstance();
            if (eDate != null) {
                endCal.setTime(eDate);
            }
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);
            endTimestamp = new Timestamp(endCal.getTime());
            return true;
        } catch (ParseException e) {
            Log.e(TAG, "parseDateRange failed", e);
            return false;
        }
    }

    // (1) Fetch room doc => teacherId, subjectCode, etc.
    private void fetchRoomDataAndThenLoadTeacher() {
        db.collection("rooms")
                .document(roomId)
                .get()
                .addOnSuccessListener(roomSnap -> {
                    if (roomSnap != null && roomSnap.exists()) {
                        teacherId   = safeGetString(roomSnap, "teacherId");
                        subjectCode = safeGetString(roomSnap, "subjectCode");
                        subjectName = safeGetString(roomSnap, "subjectName");
                        section     = safeGetString(roomSnap, "section");

                        Timestamp endTimeStamp = roomSnap.getTimestamp("endTime");
                        if (endTimeStamp != null) {
                            roomEndTime = endTimeStamp;
                        }
                        if (roomSnap.contains("schedule")) {
                            roomSchedule = (List<String>) roomSnap.get("schedule");
                            Log.d(TAG, "Got schedule: " + roomSchedule);
                        }

                        String roomStartDateStr = safeGetString(roomSnap, "startDate");
                        String roomEndDateStr   = safeGetString(roomSnap, "endDate");
                        if (!roomStartDateStr.isEmpty()) {
                            try {
                                SimpleDateFormat sdfRoom = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
                                Date parsedStart = sdfRoom.parse(roomStartDateStr);
                                if (parsedStart != null) {
                                    classStartDate = new Timestamp(parsedStart);
                                }
                            } catch (ParseException e) {
                                Log.e(TAG, "Error parsing room startDate", e);
                            }
                        }
                        if (!roomEndDateStr.isEmpty()) {
                            try {
                                SimpleDateFormat sdfRoom = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
                                Date parsedEnd = sdfRoom.parse(roomEndDateStr);
                                if (parsedEnd != null) {
                                    classEndDate = new Timestamp(parsedEnd);
                                }
                            } catch (ParseException e) {
                                Log.e(TAG, "Error parsing room endDate", e);
                            }
                        }
                    }
                    fetchTeacherDocAndThenLoadDailyCodes();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching room doc", e);
                    fetchTeacherDocAndThenLoadDailyCodes();
                });
    }

    // (2) Fetch teacher doc => teacherName => fetch daily codes
    private void fetchTeacherDocAndThenLoadDailyCodes() {
        if (teacherId == null || teacherId.isEmpty()) {
            fetchDailyCodes();
            return;
        }
        db.collection("teachers")
                .document(teacherId)
                .get()
                .addOnSuccessListener(teacherSnap -> {
                    if (teacherSnap != null && teacherSnap.exists()) {
                        final String fName = safeGetString(teacherSnap, "firstName");
                        final String mName = safeGetString(teacherSnap, "middleName");
                        final String lName = safeGetString(teacherSnap, "lastName");

                        StringBuilder sb = new StringBuilder();
                        if (!fName.isEmpty()) sb.append(fName).append(" ");
                        if (!mName.isEmpty()) sb.append(mName).append(" ");
                        if (!lName.isEmpty()) sb.append(lName);
                        teacherName = sb.toString().trim();
                    }
                    fetchDailyCodes();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching teacher doc", e);
                    fetchDailyCodes();
                });
    }

    // (2A) Fetch daily codes in range
    private void fetchDailyCodes() {
        db.collection("rooms")
                .document(roomId)
                .collection("dailyCodes")
                .whereGreaterThanOrEqualTo("generatedDate", startTimestamp)
                .whereLessThanOrEqualTo("generatedDate", endTimestamp)
                .get()
                .addOnSuccessListener(snap -> {
                    dailyCodeIds.clear();
                    if (snap != null && !snap.isEmpty()) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            dailyCodeIds.add(doc.getId());
                        }
                    }
                    loadStudents();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching daily codes", e);
                    loadStudents();
                });
    }

    // (3) Load main subcollection => get enrolled users => fetch "Class" attendance
    private void loadStudents() {
        enrolledUserIds.clear();
        studentAttendanceMap.clear();
        attendanceData.clear();

        db.collection("rooms")
                .document(roomId)
                .collection("students")
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || snap.isEmpty()) {
                        Log.e(TAG, "No students in this room: " + roomId);
                        tvNoOfStudents.setText("Enrolled Students: 0");
                        loadEventsForThisRoom();
                        return;
                    }
                    final int count = snap.size();
                    tvNoOfStudents.setText("Enrolled Students: " + count);

                    for (QueryDocumentSnapshot studentDoc : snap) {
                        enrolledUserIds.add(studentDoc.getId());
                    }
                    for (QueryDocumentSnapshot studentDoc : snap) {
                        final String userId = studentDoc.getId();
                        fetchStudentNameAndClassAttendance(userId);
                    }
                    // Next, load event attendance
                    loadEventsForThisRoom();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching students subcollection", e);
                    tvNoOfStudents.setText("Enrolled Students: 0");
                    // Next, load event attendance
                    loadEventsForThisRoom();
                });
    }

    // (3A) Class attendance => record.type="Class"
    private void fetchStudentNameAndClassAttendance(final String userId) {
        final String userIdFinal = userId;
        db.collection("students")
                .document(userIdFinal)
                .get()
                .addOnSuccessListener(studentSnapshot -> {
                    if (studentSnapshot == null || !studentSnapshot.exists()) {
                        Log.d(TAG, "Skipping user with no doc: " + userIdFinal);
                        return;
                    }
                    final String firstName = safeGetString(studentSnapshot, "firstName");
                    final String lastName  = safeGetString(studentSnapshot, "lastName");
                    final String fullName  = (firstName + " " + lastName).trim();
                    final String fullNameFinal = fullName;

                    // Put/Update student in map
                    StudentAttendance studentData = studentAttendanceMap.get(userIdFinal);
                    if (studentData == null) {
                        studentData = new StudentAttendance(userIdFinal, fullNameFinal);
                        studentAttendanceMap.put(userIdFinal, studentData);
                    } else {
                        studentData.fullName = fullNameFinal;
                    }
                    final StudentAttendance studentDataFinal = studentData;

                    // For each dailyCode => check doc (Class attendance)
                    for (String dailyCodeId : dailyCodeIds) {
                        final String dailyCodeIdFinal = dailyCodeId;
                        DocumentReference attDocRef = db.collection("rooms")
                                .document(roomId)
                                .collection("students")
                                .document(userIdFinal)
                                .collection("attendance")
                                .document(dailyCodeIdFinal);

                        attDocRef.get().addOnSuccessListener(attSnap -> {
                            if (!attSnap.exists()) {
                                // Absent record
                                AttendanceRecord absentRec = new AttendanceRecord(
                                        "Class",
                                        userIdFinal,
                                        fullNameFinal,
                                        "Absent",
                                        "",
                                        formatDailyCodeAsDate(dailyCodeIdFinal),
                                        "N/A",
                                        "N/A",
                                        "",
                                        null,
                                        null,
                                        false,
                                        false
                                );
                                studentDataFinal.records.add(absentRec);
                                attendanceData.add(absentRec);
                                refreshUI();
                            } else {
                                // Read fields
                                String statusTimeIn  = safeGetString(attSnap, "statusTimeIn");
                                String statusTimeOut = safeGetString(attSnap, "statusTimeOut");
                                Timestamp timeInStamp  = attSnap.getTimestamp("timeIn");
                                Timestamp timeOutStamp = attSnap.getTimestamp("timeOut");
                                String feedback       = safeGetString(attSnap, "feedback");

                                boolean isSameLocationTimeIn = attSnap.contains("isSameLocationTimeIn")
                                        ? attSnap.getBoolean("isSameLocationTimeIn")
                                        : false;
                                boolean isSameLocationTimeOut = attSnap.contains("isSameLocationTimeOut")
                                        ? attSnap.getBoolean("isSameLocationTimeOut")
                                        : false;

                                // Check "Failed to Timeout"
                                if (statusTimeOut.isEmpty() && timeOutStamp == null && !"Absent".equalsIgnoreCase(statusTimeIn)) {
                                    // Mark "Failed to Timeout" only if day is over
                                    if (isDayOver(dailyCodeIdFinal)) {
                                        statusTimeOut = "Failed to Timeout";
                                    }
                                }

                                final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                                String timeInStr  = (timeInStamp != null)  ? timeFormat.format(timeInStamp.toDate())  : "N/A";
                                String timeOutStr = (timeOutStamp != null) ? timeFormat.format(timeOutStamp.toDate()) : "N/A";

                                AttendanceRecord record = new AttendanceRecord(
                                        "Class",
                                        userIdFinal,
                                        fullNameFinal,
                                        statusTimeIn,
                                        statusTimeOut,
                                        formatDailyCodeAsDate(dailyCodeIdFinal),
                                        timeInStr,
                                        timeOutStr,
                                        feedback,
                                        timeOutStamp,
                                        timeInStamp,
                                        isSameLocationTimeIn,
                                        isSameLocationTimeOut
                                );
                                studentDataFinal.records.add(record);
                                attendanceData.add(record);
                                refreshUI();
                            }
                        }).addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching attendance doc " + dailyCodeIdFinal, e);
                        });
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error fetching student doc " + userIdFinal, e)
                );
    }

    // (4) Load event attendance => type="Event"
    private void loadEventsForThisRoom() {
        db.collection("events")
                .get()
                .addOnSuccessListener(eventSnap -> {
                    if (eventSnap == null || eventSnap.isEmpty()) {
                        Log.d(TAG, "No events in /events.");
                        return;
                    }
                    for (DocumentSnapshot eventDoc : eventSnap.getDocuments()) {
                        final String eventId    = eventDoc.getId();
                        final String eventTitle = safeGetString(eventDoc, "title");
                        eventDoc.getReference()
                                .collection("rooms")
                                .document(roomId)
                                .get()
                                .addOnSuccessListener(roomSnap -> {
                                    if (roomSnap.exists()) {
                                        // This room is participating in that event
                                        loadEventStudents(eventId, eventTitle);
                                    }
                                })
                                .addOnFailureListener(e ->
                                        Log.e(TAG, "Error checking event->rooms->doc", e)
                                );
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error loading /events", e)
                );
    }

    private void loadEventStudents(final String eventId, final String eventTitle) {
        db.collection("events")
                .document(eventId)
                .collection("rooms")
                .document(roomId)
                .collection("students")
                .get()
                .addOnSuccessListener(stuSnap -> {
                    if (stuSnap == null || stuSnap.isEmpty()) {
                        Log.d(TAG, "No students in event->rooms->students for " + eventId);
                        return;
                    }
                    for (QueryDocumentSnapshot studentDoc : stuSnap) {
                        final String userId = studentDoc.getId();
                        fetchStudentNameAndEventAttendance(eventId, eventTitle, userId);
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error fetching event students for " + eventId, e)
                );
    }

    /**
     * UPDATED to fetch the event-attendance doc by daily-code ID rather than a "date" field query.
     */
    private void fetchStudentNameAndEventAttendance(final String eventId,
                                                    final String eventTitle,
                                                    final String userId) {
        final String eventIdFinal    = eventId;
        final String eventTitleFinal = eventTitle;
        final String userIdFinal     = userId;

        db.collection("students")
                .document(userIdFinal)
                .get()
                .addOnSuccessListener(studentSnapshot -> {
                    if (studentSnapshot == null || !studentSnapshot.exists()) {
                        Log.d(TAG, "No /students doc for " + userIdFinal);
                        return;
                    }
                    final String firstName = safeGetString(studentSnapshot, "firstName");
                    final String lastName  = safeGetString(studentSnapshot, "lastName");
                    final String fullName  = (firstName + " " + lastName).trim();
                    final String fullNameFinal = fullName;

                    // Ensure user is in our map
                    StudentAttendance studentData = studentAttendanceMap.get(userIdFinal);
                    if (studentData == null) {
                        studentData = new StudentAttendance(userIdFinal, fullNameFinal);
                        studentAttendanceMap.put(userIdFinal, studentData);
                    } else {
                        studentData.fullName = fullNameFinal;
                    }
                    final StudentAttendance studentDataFinal = studentData;

                    // Loop over same dailyCodeIds. If doc doesn't exist => Absent
                    for (String dailyCodeId : dailyCodeIds) {
                        DocumentReference attDocRef = db.collection("events")
                                .document(eventIdFinal)
                                .collection("rooms")
                                .document(roomId)
                                .collection("students")
                                .document(userIdFinal)
                                .collection("attendance")
                                .document(dailyCodeId);

                        attDocRef.get()
                                .addOnSuccessListener(attSnap -> {
                                    if (!attSnap.exists()) {
                                        // Student is absent for that daily code
                                        AttendanceRecord absentRec = new AttendanceRecord(
                                                "Event",
                                                userIdFinal,
                                                fullNameFinal,
                                                "Absent",
                                                "",
                                                formatDailyCodeAsDate(dailyCodeId),
                                                "N/A",
                                                "N/A",
                                                "",
                                                null,
                                                null,
                                                false,
                                                false
                                        );
                                        studentDataFinal.records.add(absentRec);
                                        attendanceData.add(absentRec);
                                        refreshUI();
                                    } else {
                                        // Parse doc fields
                                        String statusTimeIn  = safeGetString(attSnap, "statusTimeIn");
                                        String statusTimeOut = safeGetString(attSnap, "statusTimeOut");
                                        Timestamp timeIn  = attSnap.getTimestamp("timeIn");
                                        Timestamp timeOut = attSnap.getTimestamp("timeOut");
                                        String feedback   = safeGetString(attSnap, "feedback");

                                        boolean isSameLocationTimeIn = attSnap.contains("isSameLocationTimeIn")
                                                ? attSnap.getBoolean("isSameLocationTimeIn")
                                                : false;
                                        boolean isSameLocationTimeOut = attSnap.contains("isSameLocationTimeOut")
                                                ? attSnap.getBoolean("isSameLocationTimeOut")
                                                : false;

                                        // Check "Failed to Timeout"
                                        if (statusTimeOut.isEmpty() && timeOut == null && !"Absent".equalsIgnoreCase(statusTimeIn)) {
                                            if (isDayOver(dailyCodeId)) {
                                                statusTimeOut = "Failed to Timeout";
                                            }
                                        }

                                        final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                                        String timeInStr  = (timeIn != null)  ? timeFormat.format(timeIn.toDate())  : "N/A";
                                        String timeOutStr = (timeOut != null) ? timeFormat.format(timeOut.toDate()) : "N/A";

                                        AttendanceRecord record = new AttendanceRecord(
                                                "Event",
                                                userIdFinal,
                                                fullNameFinal,
                                                statusTimeIn,
                                                statusTimeOut,
                                                formatDailyCodeAsDate(dailyCodeId),
                                                timeInStr,
                                                timeOutStr,
                                                feedback,
                                                timeOut,
                                                timeIn,
                                                isSameLocationTimeIn,
                                                isSameLocationTimeOut
                                        );
                                        studentDataFinal.records.add(record);
                                        attendanceData.add(record);
                                        refreshUI();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error fetching event attendance for " + userIdFinal
                                            + ", docId=" + dailyCodeId, e);
                                });
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error fetching student doc for " + userIdFinal, e)
                );
    }

    /**
     * Helper to decide if the day for a given dailyCode ("yyyyMMdd") has already passed.
     */
    private boolean isDayOver(String dailyCodeId) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            Date recordDate = sdf.parse(dailyCodeId);
            if (recordDate == null) return false;

            Calendar recordCal = Calendar.getInstance();
            recordCal.setTime(recordDate);
            recordCal.set(Calendar.HOUR_OF_DAY, 23);
            recordCal.set(Calendar.MINUTE, 59);
            recordCal.set(Calendar.SECOND, 59);
            recordCal.set(Calendar.MILLISECOND, 999);
            Date recordEndOfDay = recordCal.getTime();
            return new Date().after(recordEndOfDay);
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing daily code: " + dailyCodeId, e);
        }
        return false;
    }

    /**
     * Converts a dailyCode (e.g. "20250406") to a readable date (e.g. "Apr 6, 2025").
     */
    private String formatDailyCodeAsDate(String dailyCodeId) {
        SimpleDateFormat sdfParse = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        SimpleDateFormat sdfOut   = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        try {
            Date dateObj = sdfParse.parse(dailyCodeId);
            if (dateObj != null) {
                return sdfOut.format(dateObj);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing dailyCodeId " + dailyCodeId, e);
        }
        return dailyCodeId; // fallback
    }

    private void refreshUI() {
        runOnUiThread(() -> {
            studentsContainer.removeAllViews();
            displayStudentCards();
        });
    }

    private void displayStudentCards() {
        List<StudentAttendance> sortedList = new ArrayList<>(studentAttendanceMap.values());
        // Sort by name
        Collections.sort(sortedList, new Comparator<StudentAttendance>() {
            @Override
            public int compare(StudentAttendance s1, StudentAttendance s2) {
                if (isSortAscending) {
                    return s1.fullName.compareToIgnoreCase(s2.fullName);
                } else {
                    return s2.fullName.compareToIgnoreCase(s1.fullName);
                }
            }
        });

        for (StudentAttendance studentData : sortedList) {

            // 1) If the filter is "Class", skip anyone not in enrolledUserIds
            if ("Class".equalsIgnoreCase(currentAttendanceFilter)
                    && !enrolledUserIds.contains(studentData.userId)) {
                // This user was removed from /rooms/{roomId}/students, so skip them
                continue;
            }

            // 2) Filter by search
            if (!currentSearchQuery.isEmpty()
                    && !studentData.fullName.toLowerCase().contains(currentSearchQuery.toLowerCase())) {
                continue;
            }

            int presentCount = 0;
            int lateCount    = 0;
            int absentCount  = 0;

            // Tally statuses only for the current filter
            for (AttendanceRecord record : studentData.records) {
                if (!record.type.equalsIgnoreCase(currentAttendanceFilter)) {
                    continue;
                }
                if ("Absent".equalsIgnoreCase(record.statusTimeIn)) {
                    absentCount++;
                } else if ("Late".equalsIgnoreCase(record.statusTimeIn)) {
                    lateCount++;
                } else {
                    // Present or On Time
                    presentCount++;
                }
            }

            // Build the summary card
            final View cardView = getLayoutInflater().inflate(R.layout.item_attendance, studentsContainer, false);

            final TextView nameView   = cardView.findViewById(R.id.tvStudentName);
            final TextView statusView = cardView.findViewById(R.id.tvStatus);
            final TextView eventView  = cardView.findViewById(R.id.tvEventName);
            final TextView dateView   = cardView.findViewById(R.id.tvDate);
            final TextView timeInView = cardView.findViewById(R.id.timeIn);
            final TextView timeOutView= cardView.findViewById(R.id.timeOut);

            nameView.setText(studentData.fullName);
            statusView.setText("On Time: " + presentCount + "  Late: " + lateCount + "  Absent: " + absentCount);

            // Hide details on the summary
            eventView.setVisibility(View.GONE);
            dateView.setVisibility(View.GONE);
            timeInView.setVisibility(View.GONE);
            timeOutView.setVisibility(View.GONE);

            // On click => show details dialog
            cardView.setOnClickListener(v -> showAttendanceDialog(studentData));

            studentsContainer.addView(cardView);
        }
    }

    private void showAttendanceDialog(final StudentAttendance studentData) {
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_attendance_details, null);
        final LinearLayout attendanceListContainer = dialogView.findViewById(R.id.attendanceListContainer);

        for (AttendanceRecord record : studentData.records) {
            // Filter by type
            if (!record.type.equalsIgnoreCase(currentAttendanceFilter)) {
                continue;
            }
            // Filter by search
            if (!currentSearchQuery.isEmpty()
                    && !studentData.fullName.toLowerCase().contains(currentSearchQuery.toLowerCase())) {
                continue;
            }

            final View itemView = getLayoutInflater().inflate(R.layout.item_attendance, attendanceListContainer, false);

            final TextView nameView       = itemView.findViewById(R.id.tvStudentName);
            final TextView eventView      = itemView.findViewById(R.id.tvEventName);
            final TextView dateView       = itemView.findViewById(R.id.tvDate);
            final TextView timeInView     = itemView.findViewById(R.id.timeIn);
            final TextView timeOutView    = itemView.findViewById(R.id.timeOut);
            final TextView tvTimeInStatus = itemView.findViewById(R.id.tvStatus);
            final TextView tvTimeOutStatus= itemView.findViewById(R.id.tvTimeOutStatus);

            nameView.setText(studentData.fullName);

            tvTimeInStatus.setText("Time In Status: " + record.statusTimeIn);

            if (record.statusTimeOut != null && !record.statusTimeOut.isEmpty()) {
                tvTimeOutStatus.setVisibility(View.VISIBLE);
                tvTimeOutStatus.setText("Time Out Status: " + record.statusTimeOut);
            } else {
                tvTimeOutStatus.setVisibility(View.GONE);
            }

            dateView.setText("Date: " + record.date);
            dateView.setVisibility(View.VISIBLE);

            // Append flag icons based on location check (only for UI)
            String timeInDisplay  = "Time In: " + record.timeIn  + " " + (record.isSameLocationTimeIn ? "✔" : "✖");
            String timeOutDisplay = "Time Out: " + record.timeOut + " " + (record.isSameLocationTimeOut ? "✔" : "✖");

            timeInView.setText(timeInDisplay);
            timeOutView.setText(timeOutDisplay);

            if ("Event".equalsIgnoreCase(record.type)) {
                eventView.setVisibility(View.VISIBLE);
                eventView.setText("Type: Event");
            } else {
                eventView.setVisibility(View.GONE);
            }

            // Feedback on item click
            itemView.setOnClickListener(v -> showFeedbackDialog(record));

            attendanceListContainer.addView(itemView);
        }

        new AlertDialog.Builder(this)
                .setTitle(studentData.fullName + " Attendance Details")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showFeedbackDialog(AttendanceRecord record) {
        View feedbackView = getLayoutInflater().inflate(R.layout.dialog_feedback, null);
        TextView tvFeedback = feedbackView.findViewById(R.id.tvFeedback);

        if (tvFeedback != null) {
            if (record.feedback == null || record.feedback.trim().isEmpty()) {
                tvFeedback.setText("No feedback");
            } else {
                tvFeedback.setText(record.feedback);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Feedback")
                .setView(feedbackView)
                .setPositiveButton("Close", null)
                .show();
    }

    /**
     * Modified exportToCsv: Combines per-student and room summary into one CSV row per student.
     */
    private void exportToCsv() {
        if (studentAttendanceMap.isEmpty()) {
            Toast.makeText(this, "No attendance data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csvBuilder = new StringBuilder();

        // Compute overall room summary counts (across all students for current filter)
        int totalFailedTimeout = 0;
        int totalLeftEarly = 0;
        int totalSameLocationTimeIn = 0;
        int totalSameLocationTimeOut = 0;
        for (StudentAttendance student : studentAttendanceMap.values()) {
            for (AttendanceRecord record : student.records) {
                if (!record.type.equalsIgnoreCase(currentAttendanceFilter)) {
                    continue;
                }
                if ("Failed to Timeout".equalsIgnoreCase(record.statusTimeOut)) {
                    totalFailedTimeout++;
                }
                if ("Left Early".equalsIgnoreCase(record.statusTimeOut)) {
                    totalLeftEarly++;
                }
                if (record.isSameLocationTimeIn) {
                    totalSameLocationTimeIn++;
                }
                if (record.isSameLocationTimeOut) {
                    totalSameLocationTimeOut++;
                }
            }
        }

        // Update header row to include "Left Early"
        csvBuilder.append("Full Name,On Time,Late,Absent,Room Name,Teacher Name,Failed to Timeout,Left Early,Same Location TimeIn,Same Location TimeOut\n");

        // For each student, append a row with their summary and the room info.
        for (StudentAttendance student : studentAttendanceMap.values()) {
            // If filter is "Class", only include enrolled students.
            if ("Class".equalsIgnoreCase(currentAttendanceFilter)
                    && !enrolledUserIds.contains(student.userId)) {
                continue;
            }

            // Filter by search query.
            if (!currentSearchQuery.isEmpty()
                    && !student.fullName.toLowerCase().contains(currentSearchQuery.toLowerCase())) {
                continue;
            }

            int presentCount = 0;
            int lateCount = 0;
            int absentCount = 0;
            for (AttendanceRecord record : student.records) {
                if (!record.type.equalsIgnoreCase(currentAttendanceFilter)) {
                    continue;
                }
                if ("Absent".equalsIgnoreCase(record.statusTimeIn)) {
                    absentCount++;
                } else if ("Late".equalsIgnoreCase(record.statusTimeIn)) {
                    lateCount++;
                } else {
                    presentCount++;
                }
            }

            csvBuilder.append(escapeCommas(student.fullName)).append(",")
                    .append(presentCount).append(",")
                    .append(lateCount).append(",")
                    .append(absentCount).append(",")
                    .append(escapeCommas(subjectName)).append(",")
                    .append(escapeCommas(teacherName)).append(",")
                    .append(totalFailedTimeout).append(",")
                    .append(totalLeftEarly).append(",")
                    .append(totalSameLocationTimeIn).append(",")
                    .append(totalSameLocationTimeOut).append("\n");
        }

        final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (path == null) {
            Log.e(TAG, "Could not access Environment.DIRECTORY_DOWNLOADS");
            return;
        }

        final String fileName = "attendance_summary_" + System.currentTimeMillis() + ".csv";
        final File outFile = new File(path, fileName);

        FileWriter writer = null;
        try {
            writer = new FileWriter(outFile);
            writer.append(csvBuilder.toString());
            writer.flush();

            Log.d(TAG, "Exported CSV summary to " + outFile.getAbsolutePath());
            Toast.makeText(this,
                    "CSV exported to: " + outFile.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e(TAG, "Error writing CSV summary", e);
            Toast.makeText(this, "Error exporting CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) {}
            }
        }
    }

    private String escapeCommas(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"")) {
            field = field.replace("\"", "\"\"");
            return "\"" + field + "\"";
        }
        return field;
    }

    private String safeGetString(DocumentSnapshot snap, String field) {
        String val = snap.getString(field);
        return (val == null) ? "" : val.trim();
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
        }
    }

    // Model: Updated AttendanceRecord with location booleans (only used in UI)
    private static class AttendanceRecord {
        String type;           // "Class" or "Event"
        String userId;
        String fullName;

        String statusTimeIn;   // e.g. "On Time", "Late", "Absent"
        String statusTimeOut;  // e.g. "On Time", "Early Out", or "Failed to Timeout"

        String date;           // e.g. "Mar 30, 2025"
        String timeIn;         // e.g. "9:00 AM"
        String timeOut;        // e.g. "10:00 AM"
        String feedback;

        Timestamp timeOutStamp;
        Timestamp timeInStamp;

        // New boolean flags for location match status
        boolean isSameLocationTimeIn;
        boolean isSameLocationTimeOut;

        AttendanceRecord(String type,
                         String userId,
                         String fullName,
                         String statusTimeIn,
                         String statusTimeOut,
                         String date,
                         String timeIn,
                         String timeOut,
                         String feedback,
                         Timestamp timeOutStamp,
                         Timestamp timeInStamp,
                         boolean isSameLocationTimeIn,
                         boolean isSameLocationTimeOut) {
            this.type          = type;
            this.userId        = userId;
            this.fullName      = fullName;
            this.statusTimeIn  = statusTimeIn;
            this.statusTimeOut = statusTimeOut;
            this.date          = date;
            this.timeIn        = timeIn;
            this.timeOut       = timeOut;
            this.feedback      = feedback;
            this.timeOutStamp  = timeOutStamp;
            this.timeInStamp   = timeInStamp;
            this.isSameLocationTimeIn = isSameLocationTimeIn;
            this.isSameLocationTimeOut = isSameLocationTimeOut;
        }
    }

    // Updated StudentAttendance to store userId
    private static class StudentAttendance {
        String userId;
        String fullName;
        List<AttendanceRecord> records = new ArrayList<>();

        StudentAttendance(String userId, String fullName) {
            this.userId = userId;
            this.fullName = fullName;
        }
    }
}
