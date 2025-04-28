package com.example.smarttrack;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Teachers_Report extends AppCompatActivity {

    private static final String TAG = "Teachers_Report";

    // Drawer & Navigation
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView navUsername, navIdNumber;

    // Date pickers
    private TextView tvStartDate, tvEndDate;
    private Button btnFilter;

    // RecyclerView for rooms
    private RecyclerView roomsRecyclerView;
    private RoomsAdapter roomsAdapter; // custom adapter for listing rooms

    // For search and sorting
    private EditText searchField;
    private ImageView sortButton;
    private boolean isAscending = false;
    private List<RoomItem> originalRoomList = new ArrayList<>();

    private String uid; // teacher UID
    private String selectedStartDate = null;
    private String selectedEndDate = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_report);

        // Get the UID (teacher)
        uid = getIntent().getStringExtra("uid");
        if (uid == null) {
            uid = FirebaseAuth.getInstance().getUid();

        }

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        TextView toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setText("Reports");

        // Drawer & nav
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        navUsername = navigationView.findViewById(R.id.navUsername);
        navIdNumber = navigationView.findViewById(R.id.navIdNumber);

        ImageView menuIcon = findViewById(R.id.menuIcon);
        menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Logout button
        Button logoutButton = navigationView.findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Teachers_Report.this, Login.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        TextView manualLink = findViewById(R.id.manualLink);

        manualLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://www.figma.com/proto/Jm2THxINfHdGGAZCXLuMUr/REPORTTTTTTTTTTTTTTTTTS?node-id=17-47&t=ZpUfrv8HVrd9jggJ-1&scaling=min-zoom&content-scaling=fixed&page-id=17%3A5";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        });



        // Bottom nav icons
        findViewById(R.id.homeIcon).setOnClickListener(v -> {
            Intent intent = new Intent(Teachers_Report.this, Teachers_Home.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.roomIcon).setOnClickListener(v -> {
            Intent intent = new Intent(Teachers_Report.this, Teachers_Room.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.scheduleIcon).setOnClickListener(v -> {
            Intent intent = new Intent(Teachers_Report.this, Teachers_Calendar.class);
            intent.putExtra("uid", uid);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        // Fetch teacher data for nav header
        fetchUserDetails(uid);

        // Initialize date pickers + filter
        tvStartDate = findViewById(R.id.tvStartDate);
        tvEndDate = findViewById(R.id.tvEndDate);
        btnFilter = findViewById(R.id.btnFilter);

        tvStartDate.setOnClickListener(v -> showDatePickerDialog(true));
        tvEndDate.setOnClickListener(v -> showDatePickerDialog(false));

        btnFilter.setOnClickListener(v -> {
            if (selectedStartDate == null || selectedEndDate == null) {
                Toast.makeText(this, "Please select both start and end date.", Toast.LENGTH_SHORT).show();
                return;
            }
            // When filter button is clicked, show a loading dialog
            loadTeacherRooms();
        });

        // Setup the rooms RecyclerView
        roomsRecyclerView = findViewById(R.id.roomsRecyclerView);
        roomsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        roomsAdapter = new RoomsAdapter(new ArrayList<>());
        roomsRecyclerView.setAdapter(roomsAdapter);

        // Initialize search and sort UI (ensure these views exist in your layout)
        searchField = findViewById(R.id.searchField);
        sortButton = findViewById(R.id.sortButton);

        // Add search functionality
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterRooms(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {
                // Not used
            }
        });

        // Add sort functionality
        sortButton.setOnClickListener(v -> sortRooms());
    }

    /**
     * Shows a date picker for either start date (isStart=true) or end date (isStart=false)
     */
    private void showDatePickerDialog(boolean isStart) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);  // 0-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (DatePicker view, int selectedYear, int selectedMonth, int selectedDay) -> {
                    String dateStr = selectedYear + "-" + (selectedMonth + 1) + "-" + selectedDay;
                    if (isStart) {
                        selectedStartDate = dateStr;
                        tvStartDate.setText(dateStr);
                    } else {
                        selectedEndDate = dateStr;
                        tvEndDate.setText(dateStr);
                    }
                },
                year,
                month,
                day
        );
        datePickerDialog.show();
    }

    /**
     * Loads all rooms belonging to the teacher, populates the RecyclerView.
     * Only rooms whose room duration (startDate to endDate in the room document)
     * overlaps with the teacher-selected date range will be displayed.
     * A loading dialog is shown while the rooms are being fetched.
     */
    private void loadTeacherRooms() {
        // Show a progress dialog to indicate loading
        ProgressDialog progressDialog = new ProgressDialog(Teachers_Report.this);
        progressDialog.setMessage("Loading rooms...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        FirebaseFirestore.getInstance().collection("rooms")
                .whereEqualTo("teacherId", uid)
                .get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Toast.makeText(this, "Error loading rooms", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error fetching rooms", task.getException());
                        return;
                    }

                    List<RoomItem> roomList = new ArrayList<>();
                    // Prepare a date formatter for teacher-selected dates ("yyyy-M-d")
                    SimpleDateFormat sdfTeacher = new SimpleDateFormat("yyyy-M-d", Locale.getDefault());
                    Date teacherStart = null, teacherEnd = null;
                    try {
                        teacherStart = sdfTeacher.parse(selectedStartDate);
                        teacherEnd = sdfTeacher.parse(selectedEndDate);
                    } catch (ParseException e) {
                        Log.e(TAG, "Error parsing teacher selected dates", e);
                    }

                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String roomId = doc.getId();
                        String subjectCode = doc.getString("subjectCode");
                        String section = doc.getString("section");

                        if (subjectCode == null) subjectCode = "UnknownSubject";
                        if (section == null) section = "UnknownSection";

                        // Get room's startDate and endDate (assumed stored as strings in "d/M/yyyy" format)
                        String roomStartDateStr = doc.getString("startDate");
                        String roomEndDateStr = doc.getString("endDate");

                        // If teacher selected dates are available and room's dates are available, filter based on overlap.
                        if (teacherStart != null && teacherEnd != null && roomStartDateStr != null && roomEndDateStr != null) {
                            SimpleDateFormat sdfRoom = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
                            try {
                                Date roomStart = sdfRoom.parse(roomStartDateStr);
                                Date roomEnd = sdfRoom.parse(roomEndDateStr);
                                // Only add room if there is an overlap between the room's duration and the teacher's selected range.
                                // That is: roomStart <= teacherEnd AND roomEnd >= teacherStart
                                if (roomStart != null && roomEnd != null) {
                                    if (roomStart.after(teacherEnd) || roomEnd.before(teacherStart)) {
                                        // No overlap; skip this room.
                                        continue;
                                    }
                                }
                            } catch (ParseException e) {
                                Log.e(TAG, "Error parsing room dates for room " + roomId, e);
                                // In case of error, skip this room.
                                continue;
                            }
                        }
                        roomList.add(new RoomItem(roomId, subjectCode, section));
                    }

                    // Store the full list for filtering and sorting
                    originalRoomList = new ArrayList<>(roomList);
                    // Update the adapter with the complete list
                    roomsAdapter.setRooms(roomList);
                });
    }

    /**
     * Filters the list of rooms based on the query.
     */
    private void filterRooms(String query) {
        query = query.trim().toLowerCase();
        List<RoomItem> filteredList = new ArrayList<>();
        if (query.isEmpty()) {
            filteredList.addAll(originalRoomList);
        } else {
            for (RoomItem room : originalRoomList) {
                // Check if the query matches subjectCode, section, or roomId
                if ((room.subjectCode != null && room.subjectCode.toLowerCase().contains(query)) ||
                        (room.section != null && room.section.toLowerCase().contains(query)) ||
                        (room.roomId != null && room.roomId.toLowerCase().contains(query))) {
                    filteredList.add(room);
                }
            }
        }
        roomsAdapter.setRooms(filteredList);
    }

    /**
     * Sorts the current list of rooms based on subjectCode.
     * Toggles between ascending and descending order.
     */
    private void sortRooms() {
        isAscending = !isAscending;
        // Get the current search query to determine which list to sort
        String query = searchField.getText().toString().trim().toLowerCase();
        List<RoomItem> listToSort = new ArrayList<>();
        if (query.isEmpty()) {
            listToSort.addAll(originalRoomList);
        } else {
            // Reapply the filter to get the currently displayed list
            for (RoomItem room : originalRoomList) {
                if ((room.subjectCode != null && room.subjectCode.toLowerCase().contains(query)) ||
                        (room.section != null && room.section.toLowerCase().contains(query)) ||
                        (room.roomId != null && room.roomId.toLowerCase().contains(query))) {
                    listToSort.add(room);
                }
            }
        }
        // Sort the list based on subjectCode
        Collections.sort(listToSort, new Comparator<RoomItem>() {
            @Override
            public int compare(RoomItem r1, RoomItem r2) {
                String s1 = r1.subjectCode == null ? "" : r1.subjectCode;
                String s2 = r2.subjectCode == null ? "" : r2.subjectCode;
                return isAscending ? s1.compareToIgnoreCase(s2) : s2.compareToIgnoreCase(s1);
            }
        });
        roomsAdapter.setRooms(listToSort);
    }

    /**
     * Fetch and show teacher's name/ID in navigation header
     */
    private void fetchUserDetails(String uid) {
        FirebaseFirestore.getInstance().collection("teachers")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("firstName");
                        String lastName = document.getString("lastName");
                        String idNumber = document.getString("idNumber");

                        navUsername.setText((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName));
                        navIdNumber.setText(idNumber != null ? idNumber : "");
                    }
                })
                .addOnFailureListener(e -> {
                    navUsername.setText("Error fetching details");
                    navIdNumber.setText("");
                });
    }

    // ----------------------------------------------------------------------
    // Simple adapter to display rooms in a RecyclerView
    // ----------------------------------------------------------------------
    private class RoomsAdapter extends RecyclerView.Adapter<RoomsAdapter.RoomViewHolder> {

        private List<RoomItem> rooms;

        public RoomsAdapter(List<RoomItem> rooms) {
            this.rooms = rooms;
        }

        public void setRooms(List<RoomItem> updatedList) {
            this.rooms = updatedList;
            notifyDataSetChanged();
        }

        @Override
        public RoomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // Inflate the custom layout
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_room, parent, false);
            return new RoomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RoomViewHolder holder, int position) {
            RoomItem roomItem = rooms.get(position);

            // Combine subjectCode & section, e.g. "123 - g1"
            String display = roomItem.subjectCode + " - " + roomItem.section;

            holder.tvRoomTitle.setText(display);
            holder.tvRoomSubtitle.setText("Tap to view attendance");

            // Click listener => go to AttendanceSummary
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(Teachers_Report.this, AttendanceSummary.class);
                intent.putExtra("roomId", roomItem.roomId);
                intent.putExtra("subjectCode", roomItem.subjectCode);
                intent.putExtra("section", roomItem.section);
                intent.putExtra("startDate", selectedStartDate);
                intent.putExtra("endDate", selectedEndDate);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return rooms.size();
        }

        class RoomViewHolder extends RecyclerView.ViewHolder {
            TextView tvRoomTitle, tvRoomCreator, tvRoomSubtitle;

            RoomViewHolder(View itemView) {
                super(itemView);
                tvRoomTitle = itemView.findViewById(R.id.tvRoomTitle);
                tvRoomCreator = itemView.findViewById(R.id.tvRoomCreator);
                tvRoomSubtitle = itemView.findViewById(R.id.tvRoomSubtitle);

                // Hide the "Created by:" TextView
                tvRoomCreator.setVisibility(View.GONE);
            }
        }
    }

    private static class RoomItem {
        String roomId;
        String subjectCode;
        String section;

        RoomItem(String roomId, String subjectCode, String section) {
            this.roomId = roomId;
            this.subjectCode = subjectCode;
            this.section = section;
        }
    }
}
